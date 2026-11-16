package com.roamatlas.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.backup.BackupForegroundService
import com.roamatlas.data.backup.BackupJobController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val password: String = "",
    val isWorking: Boolean = false,
    val progressMessage: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val showResultDialog: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobController: BackupJobController,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val password = MutableStateFlow(
        savedStateHandle.get<String>(KEY_PASSWORD).orEmpty()
    )

    val uiState: StateFlow<BackupUiState> = combine(
        password,
        jobController.ui
    ) { pwd, job ->
        BackupUiState(
            password = pwd,
            isWorking = job.isWorking,
            progressMessage = job.progressMessage,
            message = job.message,
            isError = job.isError,
            showResultDialog = job.showResultDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState(
            password = password.value,
            isWorking = jobController.ui.value.isWorking,
            progressMessage = jobController.ui.value.progressMessage,
            message = jobController.ui.value.message,
            isError = jobController.ui.value.isError,
            showResultDialog = jobController.ui.value.showResultDialog
        )
    )

    fun setPassword(value: String) {
        savedStateHandle[KEY_PASSWORD] = value
        password.value = value
        if (jobController.ui.value.showResultDialog) {
            jobController.dismissResultDialog()
        }
    }

    fun dismissResultDialog() {
        jobController.dismissResultDialog()
    }

    fun export(uri: Uri) {
        if (jobController.isWorking()) return
        val pwd = password.value
        if (pwd.length < 12) {
            jobController.complete(
                message = "Use at least 12 characters for a new backup password.",
                isError = true
            )
            return
        }
        // Persist read/write access for the chosen document while export runs in the service.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        jobController.prepareExport(uri.toString(), pwd.toCharArray())
        BackupForegroundService.startExport(context)
    }

    fun restore(uri: Uri) {
        if (jobController.isWorking()) return
        val pwd = password.value
        if (pwd.isEmpty()) {
            jobController.complete(
                message = "Enter the backup password first.",
                isError = true
            )
            return
        }
        viewModelScope.launch {
            try {
                jobController.updateProgress("Copying backup into RoamAtlas…")
                val localCopy = withContext(Dispatchers.IO) { copyBackupToCache(uri) }
                jobController.prepareRestore(localCopy, pwd.toCharArray())
                BackupForegroundService.startRestore(context)
            } catch (e: Exception) {
                jobController.complete(
                    message = e.message ?: "Could not open the backup file.",
                    isError = true
                )
            }
        }
    }

    private fun copyBackupToCache(uri: Uri): File {
        val target = File(context.cacheDir, "restore-${System.currentTimeMillis()}.rabackup")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open the backup file. Try picking it again from Files.")
        require(target.length() > 0L) { "The selected backup file is empty." }
        return target
    }

    companion object {
        private const val KEY_PASSWORD = "backup_password"
    }
}
