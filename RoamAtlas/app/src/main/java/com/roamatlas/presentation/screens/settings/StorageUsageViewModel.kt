package com.roamatlas.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.local.RoamAtlasDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StorageUsageUiState(
    val databaseBytes: Long = 0,
    val filesBytes: Long = 0,
    val cacheBytes: Long = 0,
    val installedAppBytes: Long = 0,
    val locationCount: Int = 0,
    val flightCount: Int = 0,
    val counterCount: Int = 0,
    val tripCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val localDataBytes: Long get() = databaseBytes + filesBytes + cacheBytes
}

@HiltViewModel
class StorageUsageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RoamAtlasDatabase
) : ViewModel() {
    private val _uiState = MutableStateFlow(StorageUsageUiState())
    val uiState: StateFlow<StorageUsageUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                _uiState.value = withContext(Dispatchers.IO) {
                    val databaseFile = context.getDatabasePath(DATABASE_NAME)
                    val databaseBytes = listOf(
                        databaseFile,
                        File("${databaseFile.path}-wal"),
                        File("${databaseFile.path}-shm"),
                        File("${databaseFile.path}-journal")
                    ).sumOf { it.lengthIfFile() }
                    StorageUsageUiState(
                        databaseBytes = databaseBytes,
                        filesBytes = context.filesDir.directorySize() +
                            context.noBackupFilesDir.directorySize(),
                        cacheBytes = context.cacheDir.directorySize() +
                            (context.externalCacheDir?.directorySize() ?: 0),
                        installedAppBytes = File(context.applicationInfo.sourceDir).lengthIfFile(),
                        locationCount = database.locationDao().getLocationCount().first(),
                        flightCount = database.flightDao().getFlightCount().first(),
                        counterCount = database.dayTrackerDao().getAllSnapshot().size,
                        tripCount = database.tripDao().getAllSnapshot().size,
                        isLoading = false
                    )
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not calculate storage usage."
                )
            }
        }
    }

    private fun File.lengthIfFile(): Long = if (isFile) length() else 0

    private fun File.directorySize(): Long {
        if (!exists()) return 0
        return walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        private const val DATABASE_NAME = "roamatlas.db"
    }
}
