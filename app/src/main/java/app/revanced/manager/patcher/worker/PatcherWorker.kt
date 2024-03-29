package app.revanced.manager.patcher.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.revanced.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.KeystoreManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.DownloadedAppRepository
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.worker.Worker
import app.revanced.manager.domain.worker.WorkerRepository
import app.revanced.manager.patcher.Session
import app.revanced.manager.patcher.aapt.Aapt
import app.revanced.manager.patcher.logger.ManagerLogger
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.State
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileNotFoundException

class PatcherWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker<PatcherWorker.Args>(context, parameters), KoinComponent {
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val workerRepository: WorkerRepository by inject()
    private val prefs: PreferencesManager by inject()
    private val keystoreManager: KeystoreManager by inject()
    private val downloadedAppRepository: DownloadedAppRepository by inject()
    private val pm: PM by inject()
    private val fs: Filesystem by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val rootInstaller: RootInstaller by inject()

    data class Args(
        val input: SelectedApp,
        val output: String,
        val selectedPatches: PatchSelection,
        val options: Options,
        val logger: ManagerLogger,
        val downloadProgress: MutableStateFlow<Pair<Float, Float>?>,
        val patchesProgress: MutableStateFlow<Pair<Int, Int>>,
        val setInputFile: (File) -> Unit,
        val onProgress: (name: String?, state: State?, message: String?) -> Unit
    ) {
        val packageName get() = input.packageName
    }

    override suspend fun getForegroundInfo() =
        ForegroundInfo(
            1,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

    private fun createNotification(): Notification {
        val notificationIntent = Intent(applicationContext, PatcherWorker::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val channel = NotificationChannel(
            "revanced-patcher-patching", "Patching", NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager =
            ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
        notificationManager!!.createNotificationChannel(channel)
        return Notification.Builder(applicationContext, channel.id)
            .setContentTitle(applicationContext.getText(R.string.app_name))
            .setContentText(applicationContext.getText(R.string.patcher_notification_message))
            .setLargeIcon(Icon.createWithResource(applicationContext, R.drawable.ic_notification))
            .setSmallIcon(Icon.createWithResource(applicationContext, R.drawable.ic_notification))
            .setContentIntent(pendingIntent).build()
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount > 0) {
            Log.d(tag, "Android requested retrying but retrying is disabled.".logFmt())
            return Result.failure()
        }

        try {
            // This does not always show up for some reason.
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.d(tag, "Failed to set foreground info:", e)
        }

        val wakeLock: PowerManager.WakeLock =
            (applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, "$tag::Patcher").apply {
                    acquire(10 * 60 * 1000L)
                    Log.d(tag, "Acquired wakelock.")
                }

        val args = workerRepository.claimInput(this)

        return try {
            runPatcher(args)
        } finally {
            wakeLock.release()
        }
    }

    private suspend fun runPatcher(args: Args): Result {

        fun updateProgress(name: String? = null, state: State? = null, message: String? = null) =
            args.onProgress(name, state, message)

        val patchedApk = fs.tempDir.resolve("patched.apk")

        return try {
            val bundles = patchBundleRepository.bundles.first()

            // TODO: consider passing all the classes directly now that the input no longer needs to be serializable.
            val selectedBundles = args.selectedPatches.keys
            val allPatches = bundles.filterKeys { selectedBundles.contains(it) }
                .mapValues { (_, bundle) -> bundle.patchClasses(args.packageName) }

            val selectedPatches = args.selectedPatches.flatMap { (bundle, selected) ->
                allPatches[bundle]?.filter { selected.contains(it.name) }
                    ?: throw IllegalArgumentException("Patch bundle $bundle does not exist")
            }

            val aaptPath = Aapt.binary(applicationContext)?.absolutePath
                ?: throw FileNotFoundException("Could not resolve aapt.")

            val frameworkPath =
                applicationContext.cacheDir.resolve("framework").also { it.mkdirs() }.absolutePath

            val integrations = bundles.mapNotNull { (_, bundle) -> bundle.integrations }

            if (args.input is SelectedApp.Installed) {
                installedAppRepository.get(args.packageName)?.let {
                    if (it.installType == InstallType.ROOT) {
                        rootInstaller.unmount(args.packageName)
                    }
                }
            }

            // Set all patch options.
            args.options.forEach { (bundle, bundlePatchOptions) ->
                val patches = allPatches[bundle] ?: return@forEach
                bundlePatchOptions.forEach { (patchName, configuredPatchOptions) ->
                    val patchOptions = patches.single { it.name == patchName }.options
                    configuredPatchOptions.forEach { (key, value) ->
                        patchOptions[key] = value
                    }
                }
            }

            updateProgress(state = State.COMPLETED) // Loading patches

            val inputFile = when (val selectedApp = args.input) {
                is SelectedApp.Download -> {
                    downloadedAppRepository.download(
                        selectedApp.app,
                        prefs.preferSplits.get(),
                        onDownload = { args.downloadProgress.emit(it) }
                    ).also {
                        args.setInputFile(it)
                        updateProgress(state = State.COMPLETED) // Download APK
                    }
                }

                is SelectedApp.Local -> selectedApp.file.also { args.setInputFile(it) }
                is SelectedApp.Installed -> File(pm.getPackageInfo(selectedApp.packageName)!!.applicationInfo.sourceDir)
            }

            Session(
                fs.tempDir.absolutePath,
                frameworkPath,
                aaptPath,
                prefs.multithreadingDexFileWriter.get(),
                applicationContext,
                args.logger,
                inputFile,
                args.patchesProgress,
                args.onProgress
            ).use { session ->
                session.run(
                    patchedApk,
                    selectedPatches,
                    integrations
                )
            }

            keystoreManager.sign(patchedApk, File(args.output))
            updateProgress(state = State.COMPLETED) // Signing

            Log.i(tag, "Patching succeeded".logFmt())
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Exception while patching".logFmt(), e)
            updateProgress(state = State.FAILED, message = e.stackTraceToString())
            Result.failure()
        } finally {
            patchedApk.delete()
        }
    }

    companion object {
        private const val logPrefix = "[Worker]:"
        private fun String.logFmt() = "$logPrefix $this"
    }
}