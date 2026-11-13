package com.roamatlas.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::export) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> restoreUri = uri }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Replace current RoamAtlas data?") },
            text = {
                Text("Restore replaces locations, flights, trackers, trips and settings. This cannot be undone.")
            },
            confirmButton = {
                Button(onClick = {
                    restoreUri = null
                    viewModel.restore(uri)
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreUri = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Backups use AES-256 encryption and include locations, flights, trackers, trips and settings. Keep the password somewhere safe—RoamAtlas cannot recover it.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Backup password") },
                supportingText = { Text("At least 12 characters for a new backup") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    exportLauncher.launch("RoamAtlas-${LocalDate.now()}.rabackup")
                },
                enabled = !state.isWorking && state.password.length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text(" Export encrypted backup")
            }
            OutlinedButton(
                onClick = {
                    restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                enabled = !state.isWorking && state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text(" Restore encrypted backup")
            }
            if (state.isWorking) {
                CircularProgressIndicator()
                Text("Working… Keep RoamAtlas open.")
            }
            state.message?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    color = if (state.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
