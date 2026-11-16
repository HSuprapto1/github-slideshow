package com.roamatlas.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.backup.EncryptedBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val manager: EncryptedBackupManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BackupUiState(password = savedStateHandle.get<String>(KEY_PASSWORD).orEmpty())
    )
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun setPassword(password: String) {
        savedStateHandle[KEY_PASSWORD] = password
        _uiState.value = _uiState.value.copy(
            password = password,
            message = null,
            showResultDialog = false
        )
    }

    fun dismissResultDialog() {
        _uiState.value = _uiState.value.copy(showResultDialog = false)
    }

    fun export(uri: Uri) = runOperation(workingLabel = "Creating encrypted backup…") {
        val password = _uiState.value.password.toCharArray()
        try {
            val result = manager.export(uri, password) { stage ->
                _uiState.value = _uiState.value.copy(progressMessage = stage)
            }
            ResultNotice(
                message = "Backup saved successfully.\n\n${result.summary}.",
                isError = false
            )
        } finally {
            password.fill('\u0000')
        }
    }

    fun restore(uri: Uri) = runOperation(workingLabel = "Preparing backup file…") {
        val password = _uiState.value.password.toCharArray()
        try {
            // Copy into app storage first so leaving Files/Drive cannot cancel restore.
            val localCopy = withContext(Dispatchers.IO) {
                copyBackupToCache(uri)
            }
            _uiState.value = _uiState.value.copy(
                progressMessage = "Unlocking and restoring data… You can leave this screen; keep RoamAtlas open."
            )
            try {
                val result = manager.restore(localCopy, password) { stage ->
                    _uiState.value = _uiState.value.copy(progressMessage = stage)
                }
                ResultNotice(
                    message = "Restore successful.\n\n${result.summary}.\n\nOpen Dashboard to see your data.",
                    isError = false
                )
            } finally {
                localCopy.delete()
            }
        } catch (e: Exception) {
            throw e
        } finally {
            password.fill('\u0000')
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

    private fun runOperation(
        workingLabel: String,
        operation: suspend () -> ResultNotice
    ) {
        if (_uiState.value.isWorking) return
        if (_uiState.value.password.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "Enter the backup password first.",
                isError = true,
                showResultDialog = true
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            isWorking = true,
            progressMessage = workingLabel,
            message = null,
            showResultDialog = false
        )
        viewModelScope.launch {
            try {
                val notice = operation()
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    progressMessage = null,
                    message = notice.message,
                    isError = notice.isError,
                    showResultDialog = true
                )
            } catch (e: Exception) {
                val friendly = friendlyError(e)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    progressMessage = null,
                    message = friendly,
                    isError = true,
                    showResultDialog = true
                )
            }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("unlock", ignoreCase = true) ||
                text.contains("password", ignoreCase = true) ->
                "Wrong password or damaged backup file.\n\nCheck the password and try again. Your current app data was not changed if unlock failed before import."
            text.contains("not a RoamAtlas", ignoreCase = true) ->
                "That file is not a RoamAtlas encrypted backup (.rabackup)."
            text.contains("Could not open", ignoreCase = true) ->
                "Could not open the backup file. Pick the .rabackup file again from Files or Downloads."
            text.isNotBlank() -> text
            else -> "Backup operation failed. Please try again."
        }
    }

    private data class ResultNotice(val message: String, val isError: Boolean)

    companion object {
        private const val KEY_PASSWORD = "backup_password"
    }
}
