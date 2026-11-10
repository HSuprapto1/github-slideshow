package com.roamatlas.presentation.screens.timelineimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.usecase.ImportPhase
import com.roamatlas.domain.usecase.ImportProgress
import com.roamatlas.domain.usecase.ImportResult
import com.roamatlas.domain.usecase.ImportTimelineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import javax.inject.Inject

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: ImportProgress? = null,
    val lastResult: ImportResult? = null,
    val selectedFiles: List<SelectedFile> = emptyList(),
    val showFilePicker: Boolean = false,
    val showResults: Boolean = false
)

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)

@HiltViewModel
class ImportTimelineViewModel @Inject constructor(
    private val importTimelineUseCase: ImportTimelineUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    
    fun onFilesSelected(uris: List<Uri>) {
        val files = uris.mapNotNull { uri ->
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var name = "Unknown"
                var size = 0L
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = it.getString(nameIndex) ?: "Unknown"
                        if (sizeIndex >= 0) size = it.getLong(sizeIndex)
                    }
                }
                
                SelectedFile(uri, name, size)
            } catch (e: Exception) {
                null
            }
        }
        
        _uiState.value = _uiState.value.copy(
            selectedFiles = files,
            showFilePicker = false
        )
    }
    
    fun startImport() {
        val files = _uiState.value.selectedFiles
        if (files.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                showResults = false,
                lastResult = null
            )
            
            var totalInserted = 0
            var totalParsed = 0
            var totalDuplicates = 0
            var totalErrors = 0
            var earliestDate: java.time.Instant? = null
            var latestDate: java.time.Instant? = null
            var errorMessages = mutableListOf<String>()
            
            for ((index, file) in files.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    progress = ImportProgress(
                        phase = ImportPhase.READING_FILE,
                        current = index + 1,
                        total = files.size,
                        message = "Processing ${file.name}..."
                    )
                )
                
                try {
                    val inputStream = context.contentResolver.openInputStream(file.uri)
                    if (inputStream == null) {
                        errorMessages.add("Could not open ${file.name}")
                        continue
                    }
                    
                    val result = importTimelineUseCase.importSync(inputStream, file.name)
                    inputStream.close()
                    
                    if (result.success) {
                        totalParsed += result.totalParsed
                        totalInserted += result.inserted
                        totalDuplicates += result.duplicatesSkipped
                        totalErrors += result.parseErrorsSkipped
                        
                        result.earliestDate?.let { date ->
                            if (earliestDate == null || date.isBefore(earliestDate)) {
                                earliestDate = date
                            }
                        }
                        result.latestDate?.let { date ->
                            if (latestDate == null || date.isAfter(latestDate)) {
                                latestDate = date
                            }
                        }
                    } else {
                        errorMessages.add("${file.name}: ${result.errorMessage}")
                    }
                } catch (e: FileNotFoundException) {
                    errorMessages.add("File not found: ${file.name}")
                } catch (e: Exception) {
                    errorMessages.add("Error processing ${file.name}: ${e.message}")
                }
            }
            
            val finalResult = ImportResult(
                success = totalInserted > 0 || errorMessages.isEmpty(),
                totalParsed = totalParsed,
                inserted = totalInserted,
                duplicatesSkipped = totalDuplicates,
                parseErrorsSkipped = totalErrors,
                errorMessage = if (errorMessages.isNotEmpty()) errorMessages.joinToString("\n") else null,
                earliestDate = earliestDate,
                latestDate = latestDate
            )
            
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                progress = null,
                lastResult = finalResult,
                showResults = true,
                selectedFiles = emptyList()
            )
        }
    }
    
    fun showFilePicker() {
        _uiState.value = _uiState.value.copy(showFilePicker = true)
    }
    
    fun dismissFilePicker() {
        _uiState.value = _uiState.value.copy(showFilePicker = false)
    }
    
    fun dismissResults() {
        _uiState.value = _uiState.value.copy(showResults = false)
    }
    
    fun clearSelectedFiles() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptyList())
    }
}
