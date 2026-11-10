package com.roamatlas.presentation.screens.timelineimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.timeline.LocalTimelineImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalTimelineImportViewModel @Inject constructor(
    private val importer: LocalTimelineImporter
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    
    data class UiState(
        val isProcessing: Boolean = false,
        val result: LocalTimelineImporter.ImportResult? = null,
        val selectedUri: Uri? = null
    )
    
    fun importFromFolder(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedUri = uri, isProcessing = true)
        viewModelScope.launch {
            val result = importer.importFromFolder(uri)
            _uiState.value = _uiState.value.copy(isProcessing = false, result = result)
        }
    }
    
    fun importFromFile(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedUri = uri, isProcessing = true)
        viewModelScope.launch {
            val result = importer.importFromFile(uri)
            _uiState.value = _uiState.value.copy(isProcessing = false, result = result)
        }
    }
    
    fun clearResult() {
        _uiState.value = _uiState.value.copy(result = null)
    }
}
