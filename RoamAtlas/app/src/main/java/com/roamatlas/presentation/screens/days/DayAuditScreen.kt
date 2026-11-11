package com.roamatlas.presentation.screens.days

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roamatlas.data.repository.ManualDayClassification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayAuditScreen(
    viewModel: DayAuditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var choosingStartDate by remember { mutableStateOf<Boolean?>(null) }
    var showCountryPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorded Days") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Departure and return dates count as outside when any recorded point is outside the selected country. A travel day can therefore show both inside and outside evidence.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateButton(
                        label = "From",
                        date = state.startDate,
                        modifier = Modifier.weight(1f),
                        onClick = { choosingStartDate = true }
                    )
                    DateButton(
                        label = "Until",
                        date = state.endDate,
                        modifier = Modifier.weight(1f),
                        onClick = { choosingStartDate = false }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { showCountryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${flagEmoji(state.selectedCountryCode)}  " +
                            state.selectedCountryName
                    )
                }
            }
            item {
                SummaryGrid(state)
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Save this range as a Dashboard counter",
                            fontWeight = FontWeight.SemiBold
                        )
                        ChoiceRow(
                            selected = state.countInsideCountry,
                            text = "Count days inside ${state.selectedCountryName}",
                            onClick = { viewModel.setCountInside(true) }
                        )
                        ChoiceRow(
                            selected = !state.countInsideCountry,
                            text = "Count days outside ${state.selectedCountryName}",
                            onClick = { viewModel.setCountInside(false) }
                        )
                        Button(
                            onClick = viewModel::saveRangeAsCounter,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save this range")
                        }
                        state.savedMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Daily records",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(state.days, key = { it.date.toEpochDay() }) { day ->
                    DayEvidenceCard(
                        day = day,
                        selectedCountryCode = state.selectedCountryCode,
                        selectedCountryName = state.selectedCountryName,
                        onMarkOutside = { viewModel.markDayOutside(day.date) },
                        onRemoveOverride = { viewModel.removeDayOverride(day.date) }
                    )
                }
            }
            state.errorMessage?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    choosingStartDate?.let { isStart ->
        DayPickerDialog(
            initialDate = if (isStart) state.startDate else state.endDate,
            onDismiss = { choosingStartDate = null },
            onDateSelected = { date ->
                choosingStartDate = null
                if (isStart) {
                    viewModel.setRange(date, state.endDate)
                } else {
                    viewModel.setRange(state.startDate, date)
                }
            }
        )
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            countries = state.countries,
            onDismiss = { showCountryPicker = false },
            onSelect = { code, name ->
                viewModel.selectCountry(code, name)
                showCountryPicker = false
            }
        )
    }
}

@Composable
private fun SummaryGrid(state: DayAuditUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                "${state.insideDays}",
                "Inside ${state.selectedCountryName}",
                Modifier.weight(1f)
            )
            SummaryCard(
                "${state.outsideDays}",
                "Outside ${state.selectedCountryName}",
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("${state.unknownCountryDays}", "Country unknown", Modifier.weight(1f))
            SummaryCard("${state.missingDays}", "No records", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DayEvidenceCard(
    day: AuditedDay,
    selectedCountryCode: String,
    selectedCountryName: String,
    onMarkOutside: () -> Unit,
    onRemoveOverride: () -> Unit
) {
    val hasConfirmedOutside = day.evidence.any {
        it.countryCode != null && it.countryCode != selectedCountryCode
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                fontWeight = FontWeight.Bold
            )
            if (!day.hasRecordedPoint) {
                Text("No recorded locations", color = MaterialTheme.colorScheme.error)
            } else {
                day.evidence.forEach { record ->
                    val country = if (record.countryCode == null) {
                        "Country could not be identified"
                    } else {
                        "${flagEmoji(record.countryCode)}  " +
                            (record.countryName ?: record.countryCode)
                    }
                    Text(
                        "$country · ${record.pointCount} recorded points",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            when {
                day.manualClassification == ManualDayClassification.OUTSIDE -> {
                    Text(
                        "User confirmed: count as outside $selectedCountryName",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(onClick = onRemoveOverride) {
                        Text("Undo decision")
                    }
                }
                !hasConfirmedOutside && day.evidence.any { it.countryCode == null } -> {
                    Text(
                        "The recorded point is outside known country land polygons. It may be offshore.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onMarkOutside,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Count this date as outside")
                    }
                }
                !hasConfirmedOutside && !day.hasRecordedPoint -> {
                    OutlinedButton(
                        onClick = onMarkOutside,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I was outside — count this date")
                    }
                }
            }
        }
    }
}

@Composable
private fun DateButton(
    label: String,
    date: LocalDate,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) { Text("Use date") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun CountryPickerDialog(
    countries: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val matches = remember(search, countries) {
        countries.filter {
            it.first.contains(search, true) || it.second.contains(search, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose country") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.fillMaxSize(0.8f)) {
                    items(matches, key = { it.first }) { country ->
                        Text(
                            "${flagEmoji(country.first)}  ${country.second}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country.first, country.second) }
                                .padding(vertical = 12.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    return countryCode.uppercase().map {
        String(Character.toChars(it.code - 'A'.code + 0x1F1E6))
    }.joinToString("")
}
