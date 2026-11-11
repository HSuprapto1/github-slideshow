package com.roamatlas.presentation.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roamatlas.domain.model.CountingMode
import com.roamatlas.domain.model.DayTracker
import java.time.LocalDate

private enum class TrackerPeriod(val label: String) {
    ALL_HISTORY("All imported history"),
    THIS_YEAR("This calendar year"),
    LAST_12_MONTHS("Last 12 months"),
    CUSTOM("Choose start and end dates")
}

@Composable
fun AddTrackerDialog(
    countries: List<Pair<String, String>>,
    defaultCountryCode: String?,
    historyStart: LocalDate?,
    historyEnd: LocalDate?,
    existingTracker: DayTracker? = null,
    onDismiss: () -> Unit,
    onAdd: (
        name: String,
        countryCode: String,
        countryName: String,
        countingMode: CountingMode,
        countInside: Boolean,
        startDate: LocalDate?,
        endDate: LocalDate?,
        year: Int?,
        thresholdDays: Int?
    ) -> Unit
) {
    val initialCountry = countries.firstOrNull {
        it.first == (existingTracker?.countryCode ?: defaultCountryCode)
    }
        ?: countries.firstOrNull()
    var selectedCountry by remember { mutableStateOf(initialCountry) }
    var countrySearch by remember { mutableStateOf(initialCountry?.second.orEmpty()) }
    var showCountryResults by remember { mutableStateOf(false) }
    var countInside by remember {
        mutableStateOf(existingTracker?.countInsideCountry ?: true)
    }
    var period by remember {
        mutableStateOf(existingTracker.toTrackerPeriod(historyStart, historyEnd))
    }
    var name by remember { mutableStateOf(existingTracker?.name.orEmpty()) }
    var startText by remember {
        mutableStateOf((existingTracker?.startDate ?: historyStart)?.toString().orEmpty())
    }
    var endText by remember {
        mutableStateOf((existingTracker?.endDate ?: historyEnd)?.toString().orEmpty())
    }
    var thresholdText by remember {
        mutableStateOf(existingTracker?.thresholdDays?.toString().orEmpty())
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val matches = remember(countrySearch, countries) {
        countries.filter { (code, countryName) ->
            countryName.contains(countrySearch, ignoreCase = true) ||
                code.contains(countrySearch, ignoreCase = true)
        }.take(8)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingTracker == null) "Add a day counter" else "Edit day counter")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                item {
                    Text(
                        "Choose a country, what to count, and the time period.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = countrySearch,
                        onValueChange = {
                            countrySearch = it
                            showCountryResults = true
                            selectedCountry = null
                        },
                        label = { Text("Country") },
                        placeholder = { Text("Type a country name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showCountryResults && countrySearch.isNotBlank()) {
                    items(matches) { country ->
                        Text(
                            text = "${flagEmoji(country.first)}  ${country.second}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCountry = country
                                    countrySearch = country.second
                                    showCountryResults = false
                                }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Count", style = MaterialTheme.typography.labelLarge)
                    SelectionRow(
                        selected = countInside,
                        text = "Days inside this country",
                        onClick = { countInside = true }
                    )
                    SelectionRow(
                        selected = !countInside,
                        text = "Days outside this country",
                        onClick = { countInside = false }
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Time period", style = MaterialTheme.typography.labelLarge)
                    TrackerPeriod.entries.forEach { option ->
                        SelectionRow(
                            selected = period == option,
                            text = option.label,
                            onClick = { period = option }
                        )
                    }
                }

                if (period == TrackerPeriod.CUSTOM) {
                    item {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text("Start date") },
                            supportingText = { Text("Example: 2025-01-01") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            label = { Text("End date") },
                            supportingText = { Text("Example: 2025-12-31") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Counter name (optional)") },
                        placeholder = { Text("Example: UK tax days") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter(Char::isDigit) },
                        label = { Text("Alert after this many days (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    validationMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val country = selectedCountry
                    if (country == null) {
                        validationMessage = "Please choose a country from the list."
                        return@Button
                    }
                    val customStart = startText.toLocalDateOrNull()
                    val customEnd = endText.toLocalDateOrNull()
                    if (period == TrackerPeriod.CUSTOM &&
                        (customStart == null || customEnd == null || customEnd < customStart)
                    ) {
                        validationMessage = "Enter valid dates, with the end date after the start."
                        return@Button
                    }

                    val (mode, start, end, year) = when (period) {
                        TrackerPeriod.ALL_HISTORY -> TrackerValues(
                            CountingMode.CUSTOM_DATE_RANGE,
                            historyStart ?: LocalDate.of(2000, 1, 1),
                            historyEnd ?: LocalDate.now(),
                            null
                        )
                        TrackerPeriod.THIS_YEAR -> TrackerValues(
                            CountingMode.CALENDAR_YEAR, null, null, LocalDate.now().year
                        )
                        TrackerPeriod.LAST_12_MONTHS -> TrackerValues(
                            CountingMode.ROLLING_12_MONTHS, null, null, null
                        )
                        TrackerPeriod.CUSTOM -> TrackerValues(
                            CountingMode.CUSTOM_DATE_RANGE, customStart, customEnd, null
                        )
                    }
                    onAdd(
                        name,
                        country.first,
                        country.second,
                        mode,
                        countInside,
                        start,
                        end,
                        year,
                        thresholdText.toIntOrNull()
                    )
                }
            ) {
                Text(if (existingTracker == null) "Add counter" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun DayTracker?.toTrackerPeriod(
    historyStart: LocalDate?,
    historyEnd: LocalDate?
): TrackerPeriod = when (this?.countingMode) {
    null -> TrackerPeriod.ALL_HISTORY
    CountingMode.CALENDAR_YEAR -> TrackerPeriod.THIS_YEAR
    CountingMode.ROLLING_12_MONTHS -> TrackerPeriod.LAST_12_MONTHS
    CountingMode.CUSTOM_START_UNTIL_TODAY -> TrackerPeriod.CUSTOM
    CountingMode.CUSTOM_DATE_RANGE -> {
        if (startDate == historyStart && endDate == historyEnd) {
            TrackerPeriod.ALL_HISTORY
        } else {
            TrackerPeriod.CUSTOM
        }
    }
}

private data class TrackerValues(
    val mode: CountingMode,
    val start: LocalDate?,
    val end: LocalDate?,
    val year: Int?
)

@Composable
private fun SelectionRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(text)
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(trim())
} catch (_: Exception) {
    null
}

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    return countryCode.uppercase().map {
        String(Character.toChars(it.code - 'A'.code + 0x1F1E6))
    }.joinToString("")
}
