package com.roamatlas.presentation.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.backup.EncryptedBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val password: String = "",
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: EncryptedBackupManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, message = null)
    }

    fun export(uri: Uri) = runOperation {
        val password = _uiState.value.password.toCharArray()
        try {
            val result = manager.export(uri, password)
            "Encrypted backup created: ${result.summary}."
        } finally {
            password.fill('\u0000')
        }
    }

    fun restore(uri: Uri) = runOperation {
        val password = _uiState.value.password.toCharArray()
        try {
            val result = manager.restore(uri, password)
            "Backup restored: ${result.summary}."
        } finally {
            password.fill('\u0000')
        }
    }

    private fun runOperation(operation: suspend () -> String) {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    message = operation(),
                    isError = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    message = e.message ?: "Backup operation failed",
                    isError = true
                )
            }
        }
    }
}
