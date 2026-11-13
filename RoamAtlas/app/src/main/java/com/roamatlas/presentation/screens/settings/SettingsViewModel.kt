package com.roamatlas.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.roamatlas.data.email.GmailFlightImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val connectedGmail: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val gmailFlightImporter: GmailFlightImporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshAccount()
    }

    fun refreshAccount() {
        _uiState.value = SettingsUiState(
            connectedGmail = gmailFlightImporter.getSignedInEmail()
                .takeIf { gmailFlightImporter.isSignedIn() }
        )
    }
}
