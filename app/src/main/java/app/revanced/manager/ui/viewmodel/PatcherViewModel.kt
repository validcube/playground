package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.revanced.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.worker.WorkerRepository
import app.revanced.manager.patcher.logger.ManagerLogger
import app.revanced.manager.patcher.worker.PatcherWorker
import app.revanced.manager.service.InstallService
import app.revanced.manager.ui.destination.Destination
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.model.Step
import app.revanced.manager.ui.model.StepCategory
import app.revanced.manager.util.PM
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Files
import java.util.UUID

@Stable
class PatcherViewModel(
    private val input: Destination.Patcher
) : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val fs: Filesystem by inject()
    private val pm: PM by inject()
    private val workerRepository: WorkerRepository by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val rootInstaller: RootInstaller by inject()

    private var installedApp: InstalledApp? = null
    val packageName: String = input.selectedApp.packageName
    var installedPackageName by mutableStateOf<String?>(null)
        private set
    var isInstalling by mutableStateOf(false)
        private set

    private val tempDir = fs.tempDir.resolve("installer").also {
        it.deleteRecursively()
        it.mkdirs()
    }
    private var inputFile: File? = null
    private val outputFile = tempDir.resolve("output.apk")

    private val workManager = WorkManager.getInstance(app)
    private val logger = ManagerLogger()

    val patchesProgress = MutableStateFlow(Pair(0, input.selectedPatches.values.sumOf { it.size }))
    private val downloadProgress = MutableStateFlow<Pair<Float, Float>?>(null)
    val steps = generateSteps(
        app,
        input.selectedApp,
        downloadProgress
    ).toMutableStateList()
    private var currentStepIndex = 0

    private val patcherWorkerId: UUID =
        workerRepository.launchExpedited<PatcherWorker, PatcherWorker.Args>(
            "patching", PatcherWorker.Args(
                input.selectedApp,
                outputFile.path,
                input.selectedPatches,
                input.options,
                logger,
                downloadProgress,
                patchesProgress,
                setInputFile = { inputFile = it },
                onProgress = { name, state, message ->
                    steps[currentStepIndex] = steps[currentStepIndex].run {
                        copy(
                            name = name ?: this.name,
                            state = state ?: this.state,
                            message = message ?: this.message
                        )
                    }

                    if (state == State.COMPLETED && currentStepIndex != steps.lastIndex) {
                        currentStepIndex++

                        steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
                    }
                }
            )
        )

    val patcherSucceeded =
        workManager.getWorkInfoByIdLiveData(patcherWorkerId).map { workInfo: WorkInfo ->
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> true
                WorkInfo.State.FAILED -> false
                else -> null
            }
        }

    private val installBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                InstallService.APP_INSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(InstallService.EXTRA_INSTALL_STATUS, -999)
                    val extra = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)!!

                    if (pmStatus == PackageInstaller.STATUS_SUCCESS) {
                        app.toast(app.getString(R.string.install_app_success))
                        installedPackageName =
                            intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME)
                        viewModelScope.launch {
                            installedAppRepository.addOrUpdate(
                                installedPackageName!!,
                                packageName,
                                input.selectedApp.version,
                                InstallType.DEFAULT,
                                input.selectedPatches
                            )
                        }
                    } else {
                        app.toast(app.getString(R.string.install_app_fail, extra))
                    }
                }
            }
        }
    }

    init { // TODO: navigate away when system-initiated process death is detected because it is not possible to recover from it.
        ContextCompat.registerReceiver(app, installBroadcastReceiver, IntentFilter().apply {
            addAction(InstallService.APP_INSTALL_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        viewModelScope.launch {
            installedApp = installedAppRepository.get(packageName)
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.unregisterReceiver(installBroadcastReceiver)
        workManager.cancelWorkById(patcherWorkerId)

        when (val selectedApp = input.selectedApp) {
            is SelectedApp.Local -> {
                if (selectedApp.temporary) selectedApp.file.delete()
            }

            is SelectedApp.Installed -> {
                try {
                    installedApp?.let {
                        if (it.installType == InstallType.ROOT) {
                            rootInstaller.mount(packageName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to mount", e)
                    app.toast(app.getString(R.string.failed_to_mount, e.simpleMessage()))
                }
            }

            else -> Unit
        }

        tempDir.deleteRecursively()
    }

    fun export(uri: Uri?) = viewModelScope.launch {
        uri?.let {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(it)
                    .use { stream -> Files.copy(outputFile.toPath(), stream) }
            }
            app.toast(app.getString(R.string.save_apk_success))
        }
    }

    fun exportLogs(context: Context) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, logger.export())
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    fun open() = installedPackageName?.let(pm::launch)

    fun install(installType: InstallType) = viewModelScope.launch {
        try {
            isInstalling = true
            when (installType) {
                InstallType.DEFAULT -> {
                    pm.installApp(listOf(outputFile))
                }

                InstallType.ROOT -> {
                    try {
                        val label = with(pm) {
                            getPackageInfo(outputFile)?.label()
                                ?: throw Exception("Failed to load application info")
                        }

                        rootInstaller.install(
                            outputFile,
                            inputFile,
                            packageName,
                            input.selectedApp.version,
                            label
                        )

                        installedAppRepository.addOrUpdate(
                            packageName,
                            packageName,
                            input.selectedApp.version,
                            InstallType.ROOT,
                            input.selectedPatches
                        )

                        rootInstaller.mount(packageName)

                        installedPackageName = packageName

                        app.toast(app.getString(R.string.install_app_success))
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to install as root", e)
                        app.toast(app.getString(R.string.install_app_fail, e.simpleMessage()))
                        try {
                            rootInstaller.uninstall(packageName)
                        } catch (_: Exception) {  }
                    }
                }
            }
        } finally {
            isInstalling = false
        }
    }

    companion object {
        fun generateSteps(
            context: Context,
            selectedApp: SelectedApp,
            downloadProgress: StateFlow<Pair<Float, Float>?>? = null
        ): List<Step> {
            return listOfNotNull(
                Step(
                    context.getString(R.string.patcher_step_load_patches),
                    StepCategory.PREPARING,
                    state = State.RUNNING
                ),
                Step(
                    context.getString(R.string.download_apk),
                    StepCategory.PREPARING,
                    downloadProgress = downloadProgress
                ).takeIf { selectedApp is SelectedApp.Download },
                Step(
                    context.getString(R.string.patcher_step_unpack),
                    StepCategory.PREPARING
                ),
                Step(
                    context.getString(R.string.patcher_step_integrations),
                    StepCategory.PREPARING
                ),

                Step(
                    context.getString(R.string.apply_patches),
                    StepCategory.PATCHING
                ),

                Step(context.getString(R.string.patcher_step_write_patched), StepCategory.SAVING),
                Step(context.getString(R.string.patcher_step_sign_apk), StepCategory.SAVING)
            )
        }
    }
}