package com.roamatlas.presentation.screens.timelineimport

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimelineImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocalTimelineImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var openedSettings by rememberSaveable { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your past Timeline") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Your history is on this phone",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Google locked the website download. The readable copy is already on this phone. " +
                            "The button that unlocks it is in the phone Settings app, not in Google Maps and not on the Google website.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (uiState.isProcessing) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val progress = uiState.progress
                        if (progress != null && progress.percent > 0) {
                            LinearProgressIndicator(
                                progress = { progress.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(progress?.message ?: "Reading your Timeline…")
                        Text(
                            progress?.let {
                                "${it.percent}% • ${it.processed} read • ${it.imported} new"
                            } ?: "A long history can take several minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            uiState.result?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (result.success) "Saved" else "Not saved",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(result.message)
                        if (result.success) {
                            Text("${result.locationsImported} new places")
                            if (result.placesFound > 0) {
                                Text("${result.placesFound} visits")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (result.success) {
                                Button(onClick = onNavigateBack) { Text("See them") }
                            }
                            TextButton(onClick = { viewModel.clearResult() }) { Text("Try again") }
                        }
                    }
                }
            }

            if (!uiState.isProcessing && uiState.result == null) {
                StepBlock(number = "1", title = "Open phone Settings") {
                    Text("Tap the button. It opens the Location page of your phone Settings.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            openLocationSettings(context)
                            openedSettings = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Location settings")
                    }
                }

                StepBlock(number = "2", title = "Export Timeline data") {
                    Text("On that screen, tap these words in order:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Bullet("Location services")
                    Bullet("Timeline")
                    Bullet("Export Timeline data")
                    Bullet("Continue")
                    Bullet("Save it in Downloads")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Wait until the phone says Export complete. The file is usually named Timeline.json.",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cannot find those words? At the top of Settings, tap the magnifying glass and type Timeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                StepBlock(number = "3", title = "Bring the file back here") {
                    Text("Come back to RoamAtlas and choose the file you just saved.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            fileLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (openedSettings) "Choose Timeline.json" else "Choose the Timeline file")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Do not use the Google website",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "The file from takeout.google.com stays locked. RoamAtlas cannot open that one. " +
                                "Only Export Timeline data, inside phone Settings, creates a file this app can read."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBlock(
    number: String,
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

private fun openLocationSettings(context: android.content.Context) {
    val candidates = listOf(
        Intent().setComponent(
            ComponentName(
                "com.google.android.gms",
                "com.google.android.gms.location.settings.LocationHistorySettingsActivity"
            )
        ),
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        val canOpen = intent.resolveActivity(context.packageManager) != null
        if (!canOpen) continue
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            continue
        }
    }
}
