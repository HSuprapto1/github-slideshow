package com.roamatlas.domain.usecase

import com.roamatlas.data.timeline.GoogleTimelineParser
import com.roamatlas.data.timeline.ParseError
import com.roamatlas.data.timeline.ParseResult
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

data class ImportProgress(
    val phase: ImportPhase,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)

enum class ImportPhase {
    READING_FILE,
    PARSING,
    DETECTING_DUPLICATES,
    INSERTING,
    COMPLETE,
    ERROR
}

data class ImportResult(
    val success: Boolean,
    val totalParsed: Int = 0,
    val inserted: Int = 0,
    val duplicatesSkipped: Int = 0,
    val parseErrorsSkipped: Int = 0,
    val parseErrors: List<ParseError> = emptyList(),
    val errorMessage: String? = null,
    val earliestDate: java.time.Instant? = null,
    val latestDate: java.time.Instant? = null
)

class ImportTimelineUseCase @Inject constructor(
    private val parser: GoogleTimelineParser,
    private val locationRepository: LocationRepository
) {
    
    fun import(inputStream: InputStream, fileName: String): Flow<ImportProgress> = flow {
        emit(ImportProgress(ImportPhase.READING_FILE, message = "Reading $fileName..."))
        
        emit(ImportProgress(ImportPhase.PARSING, message = "Parsing location data..."))
        
        val parseResult = withContext(Dispatchers.IO) {
            parser.parse(inputStream, fileName)
        }
        
        when (parseResult) {
            is ParseResult.Error -> {
                emit(ImportProgress(
                    ImportPhase.ERROR,
                    message = parseResult.message
                ))
                return@flow
            }
            is ParseResult.Success -> {
                val locations = parseResult.locations
                val totalParsed = locations.size
                
                if (locations.isEmpty()) {
                    emit(ImportProgress(
                        ImportPhase.COMPLETE,
                        message = "No valid locations found in file"
                    ))
                    return@flow
                }
                
                emit(ImportProgress(
                    ImportPhase.DETECTING_DUPLICATES,
                    total = totalParsed,
                    message = "Checking for duplicates..."
                ))
                
                val (newLocations, duplicateCount) = withContext(Dispatchers.IO) {
                    filterDuplicates(locations)
                }
                
                if (newLocations.isEmpty()) {
                    emit(ImportProgress(
                        ImportPhase.COMPLETE,
                        total = totalParsed,
                        message = "All $totalParsed locations already exist in database"
                    ))
                    return@flow
                }
                
                emit(ImportProgress(
                    ImportPhase.INSERTING,
                    current = 0,
                    total = newLocations.size,
                    message = "Inserting ${newLocations.size} new locations..."
                ))
                
                val insertedIds = withContext(Dispatchers.IO) {
                    val batchSize = 500
                    val allIds = mutableListOf<Long>()
                    
                    newLocations.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                        val ids = locationRepository.insertLocations(batch)
                        allIds.addAll(ids)
                    }
                    
                    allIds
                }
                
                val insertedCount = insertedIds.count { it > 0 }
                
                val sortedLocations = newLocations.sortedBy { it.timestamp }
                val earliestDate = sortedLocations.firstOrNull()?.timestamp
                val latestDate = sortedLocations.lastOrNull()?.timestamp
                
                emit(ImportProgress(
                    ImportPhase.COMPLETE,
                    current = insertedCount,
                    total = totalParsed,
                    message = buildCompletionMessage(
                        totalParsed = totalParsed,
                        inserted = insertedCount,
                        duplicates = duplicateCount,
                        parseErrors = parseResult.skippedCount,
                        earliestDate = earliestDate,
                        latestDate = latestDate
                    )
                ))
            }
        }
    }
    
    suspend fun importSync(inputStream: InputStream, fileName: String): ImportResult {
        return withContext(Dispatchers.IO) {
            val parseResult = parser.parse(inputStream, fileName)
            
            when (parseResult) {
                is ParseResult.Error -> {
                    ImportResult(
                        success = false,
                        errorMessage = parseResult.message
                    )
                }
                is ParseResult.Success -> {
                    val locations = parseResult.locations
                    
                    if (locations.isEmpty()) {
                        return@withContext ImportResult(
                            success = true,
                            totalParsed = 0,
                            errorMessage = "No valid locations found"
                        )
                    }
                    
                    val (newLocations, duplicateCount) = filterDuplicates(locations)
                    
                    val insertedIds = if (newLocations.isNotEmpty()) {
                        locationRepository.insertLocations(newLocations)
                    } else {
                        emptyList()
                    }
                    
                    val insertedCount = insertedIds.count { it > 0 }
                    
                    val sortedLocations = locations.sortedBy { it.timestamp }
                    
                    ImportResult(
                        success = true,
                        totalParsed = locations.size,
                        inserted = insertedCount,
                        duplicatesSkipped = duplicateCount,
                        parseErrorsSkipped = parseResult.skippedCount,
                        parseErrors = parseResult.errors,
                        earliestDate = sortedLocations.firstOrNull()?.timestamp,
                        latestDate = sortedLocations.lastOrNull()?.timestamp
                    )
                }
            }
        }
    }
    
    private suspend fun filterDuplicates(locations: List<LocationRecord>): Pair<List<LocationRecord>, Int> {
        val newLocations = mutableListOf<LocationRecord>()
        var duplicateCount = 0
        
        for (location in locations) {
            val sourceId = location.originalSourceId
            if (sourceId != null && locationRepository.isDuplicate(sourceId)) {
                duplicateCount++
            } else {
                newLocations.add(location)
            }
        }
        
        return Pair(newLocations, duplicateCount)
    }
    
    private fun buildCompletionMessage(
        totalParsed: Int,
        inserted: Int,
        duplicates: Int,
        parseErrors: Int,
        earliestDate: java.time.Instant?,
        latestDate: java.time.Instant?
    ): String {
        val parts = mutableListOf<String>()
        parts.add("Import complete!")
        parts.add("$inserted new locations added")
        
        if (duplicates > 0) {
            parts.add("$duplicates duplicates skipped")
        }
        if (parseErrors > 0) {
            parts.add("$parseErrors records couldn't be parsed")
        }
        
        if (earliestDate != null && latestDate != null) {
            val dateFormatter = java.time.format.DateTimeFormatter
                .ofPattern("MMM d, yyyy")
                .withZone(java.time.ZoneId.systemDefault())
            
            parts.add("Date range: ${dateFormatter.format(earliestDate)} - ${dateFormatter.format(latestDate)}")
        }
        
        return parts.joinToString("\n")
    }
}
