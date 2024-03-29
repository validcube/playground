package app.revanced.manager.domain.bundles

import android.util.Log
import androidx.compose.runtime.Stable
import app.revanced.manager.patcher.patch.PatchBundle
import app.revanced.manager.util.tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.io.OutputStream

/**
 * A [PatchBundle] source.
 */
@Stable
sealed class PatchBundleSource(val name: String, val uid: Int, directory: File) {
    protected val patchesFile = directory.resolve("patches.jar")
    protected val integrationsFile = directory.resolve("integrations.apk")

    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    /**
     * Returns true if the bundle has been downloaded to local storage.
     */
    fun hasInstalled() = patchesFile.exists()

    protected fun patchBundleOutputStream(): OutputStream = with(patchesFile) {
        // Android 14+ requires dex containers to be readonly.
        try {
            setWritable(true, true)
            outputStream()
        } finally {
            setReadOnly()
        }
    }

    private fun load(): State {
        if (!hasInstalled()) return State.Missing

        return try {
            State.Loaded(PatchBundle(patchesFile, integrationsFile.takeIf(File::exists)))
        } catch (t: Throwable) {
            Log.e(tag, "Failed to load patch bundle $name", t)
            State.Failed(t)
        }
    }

    fun reload() {
        _state.value = load()
    }

    sealed interface State {
        fun patchBundleOrNull(): PatchBundle? = null

        data object Missing : State
        data class Failed(val throwable: Throwable) : State
        data class Loaded(val bundle: PatchBundle) : State {
            override fun patchBundleOrNull() = bundle
        }
    }

    companion object {
        val PatchBundleSource.isDefault get() = uid == 0
        val PatchBundleSource.asRemoteOrNull get() = this as? RemotePatchBundle
        fun PatchBundleSource.propsOrNullFlow() = asRemoteOrNull?.propsFlow() ?: flowOf(null)
    }
}