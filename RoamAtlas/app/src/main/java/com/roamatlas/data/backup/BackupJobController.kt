package com.roamatlas.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupJobKind {
    IDLE,
    EXPORT,
    RESTORE
}

data class BackupJobUi(
    val kind: BackupJobKind = BackupJobKind.IDLE,
    val isWorking: Boolean = false,
    val progressMessage: String? = null,
    val progressPercent: Int? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val showResultDialog: Boolean = false
)

/**
 * Holds in-flight backup/restore work outside any screen ViewModel so leaving
 * the Backup screen or switching apps does not cancel the job.
 */
@Singleton
class BackupJobController @Inject constructor() {
    private val _ui = MutableStateFlow(BackupJobUi())
    val ui: StateFlow<BackupJobUi> = _ui.asStateFlow()

    private val pendingPassword = AtomicReference<CharArray?>(null)
    private val pendingFile = AtomicReference<File?>(null)
    private val pendingExportUri = AtomicReference<String?>(null)

    fun isWorking(): Boolean = _ui.value.isWorking

    fun prepareRestore(file: File, password: CharArray) {
        clearSecrets(deletePendingFile = true)
        pendingFile.set(file)
        pendingPassword.set(password.copyOf())
        _ui.value = BackupJobUi(
            kind = BackupJobKind.RESTORE,
            isWorking = true,
            progressMessage = "Starting restore in background…",
            progressPercent = 0,
            showResultDialog = false
        )
    }

    fun prepareExport(uriString: String, password: CharArray) {
        clearSecrets(deletePendingFile = true)
        pendingExportUri.set(uriString)
        pendingPassword.set(password.copyOf())
        _ui.value = BackupJobUi(
            kind = BackupJobKind.EXPORT,
            isWorking = true,
            progressMessage = "Starting backup in background…",
            progressPercent = 0,
            showResultDialog = false
        )
    }

    fun takePassword(): CharArray? = pendingPassword.getAndSet(null)

    fun takeRestoreFile(): File? = pendingFile.getAndSet(null)

    fun takeExportUri(): String? = pendingExportUri.getAndSet(null)

    fun updateProgress(progress: BackupProgress) {
        _ui.value = _ui.value.copy(
            isWorking = true,
            progressMessage = progress.message,
            progressPercent = progress.percent,
            showResultDialog = false
        )
    }

    fun updateProgress(message: String, percent: Int? = null) {
        updateProgress(BackupProgress(message, percent))
    }

    fun complete(message: String, isError: Boolean) {
        clearSecrets(deletePendingFile = true)
        _ui.value = BackupJobUi(
            kind = BackupJobKind.IDLE,
            isWorking = false,
            progressMessage = null,
            progressPercent = if (isError) null else 100,
            message = message,
            isError = isError,
            showResultDialog = true
        )
    }

    fun dismissResultDialog() {
        _ui.value = _ui.value.copy(showResultDialog = false)
    }

    fun clearSecrets(deletePendingFile: Boolean = true) {
        pendingPassword.getAndSet(null)?.fill('\u0000')
        val file = pendingFile.getAndSet(null)
        if (deletePendingFile) file?.delete()
        pendingExportUri.set(null)
    }
}
