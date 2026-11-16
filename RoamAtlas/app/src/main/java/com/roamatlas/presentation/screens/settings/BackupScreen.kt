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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::export) }
    // GetContent is more reliable on Samsung when returning from Files/Drive.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    if (state.showResultDialog && state.message != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResultDialog,
            title = {
                Text(if (state.isError) "Backup failed" else "Backup complete")
            },
            text = { Text(state.message!!) },
            confirmButton = {
                Button(onClick = viewModel::dismissResultDialog) {
                    Text("OK")
                }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                if (!state.isWorking) pendingRestoreUri = null
            },
            title = { Text("Replace current RoamAtlas data?") },
            text = {
                Text(
                    "Restore replaces locations, flights, trackers, trips and settings with this backup. " +
                        "RoamAtlas will copy the file first, so switching apps during restore is safer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = uri
                        pendingRestoreUri = null
                        viewModel.restore(chosen)
                    },
                    enabled = !state.isWorking
                ) { Text("Restore now") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreUri = null },
                    enabled = !state.isWorking
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !state.isWorking) {
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Backups use AES-256 encryption and include locations, flights, trackers, trips and settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "1. Type your backup password\n" +
                            "2. Tap Restore and pick the .rabackup file\n" +
                            "3. Wait for the success dialog — keep RoamAtlas open until then",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Backup password") },
                supportingText = {
                    Text(
                        if (state.password.isEmpty()) {
                            "Required for restore. New backups need at least 12 characters."
                        } else {
                            "Password entered · Restore is unlocked"
                        }
                    )
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !state.isWorking
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                },
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
                onClick = { restoreLauncher.launch("*/*") },
                enabled = !state.isWorking && state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text(" Restore encrypted backup")
            }
            if (state.isWorking) {
                CircularProgressIndicator()
                Text(
                    "Working… Keep RoamAtlas open until the result dialog appears.",
                    fontWeight = FontWeight.SemiBold
                )
                state.progressMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!state.showResultDialog) {
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
}
