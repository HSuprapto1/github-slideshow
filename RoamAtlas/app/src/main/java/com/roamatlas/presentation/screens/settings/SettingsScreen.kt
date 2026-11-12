package com.roamatlas.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roamatlas.BuildConfig

private enum class SettingsInformation {
    PRIVACY,
    ABOUT,
    HELP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToImport: () -> Unit = {},
    onNavigateToGpsTracking: () -> Unit = {},
    onNavigateToHomeCountry: () -> Unit = {},
    onNavigateToImportFlights: () -> Unit = {},
    onNavigateToFlightReminders: () -> Unit = {},
    onNavigateToLocalTimelineImport: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToStorageUsage: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var informationDialog by remember { mutableStateOf<SettingsInformation?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                SettingsSection(title = "Data Import")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Unlock phone Timeline",
                        subtitle = "Get the readable file from phone Settings",
                        onClick = onNavigateToLocalTimelineImport
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.CloudUpload,
                        title = "Import Google Takeout",
                        subtitle = "Import JSON export from Google Takeout",
                        onClick = onNavigateToImport
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "Encrypted backup",
                        subtitle = "Export or restore all RoamAtlas data",
                        onClick = onNavigateToBackup
                    )
                }
            }
            
            item {
                SettingsSection(title = "GPS Tracking")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.MyLocation,
                        title = "GPS Tracking",
                        subtitle = "Start, stop, and configure location tracking",
                        onClick = onNavigateToGpsTracking
                    )
                }
            }
            
            item {
                SettingsSection(title = "Travel Settings")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Home,
                        title = "Home Country",
                        subtitle = "Set your home country for travel statistics",
                        onClick = onNavigateToHomeCountry
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.FlightTakeoff,
                        title = "Import Flights",
                        subtitle = uiState.connectedGmail?.let {
                            "Connected Gmail: $it"
                        } ?: "Gmail not connected · tap to connect or add flights",
                        onClick = onNavigateToImportFlights
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Flight Reminders",
                        subtitle = "Distance estimates with links to live traffic and status",
                        onClick = onNavigateToFlightReminders
                    )
                }
            }
            
            item {
                SettingsSection(title = "Appearance")
            }
            
            item {
                var dynamicColor by remember { mutableStateOf(true) }
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Default.Palette,
                        title = "Dynamic Colors",
                        subtitle = "Use Material You wallpaper colors",
                        checked = dynamicColor,
                        onCheckedChange = { dynamicColor = it }
                    )
                }
            }
            
            item {
                SettingsSection(title = "Privacy & Data")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "How RoamAtlas handles your information",
                        onClick = { informationDialog = SettingsInformation.PRIVACY }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Storage,
                        title = "Storage Usage",
                        subtitle = "Database size and management",
                        onClick = onNavigateToStorageUsage
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "Delete All Data",
                        subtitle = "Permanently remove all location records",
                        onClick = { /* TODO */ },
                        isDestructive = true
                    )
                }
            }
            
            item {
                SettingsSection(title = "About")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About RoamAtlas",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        onClick = { informationDialog = SettingsInformation.ABOUT }
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = "Help & Feedback",
                        subtitle = "Report issues, request features",
                        onClick = { informationDialog = SettingsInformation.HELP }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RoamAtlas v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Personal Travel History Tracker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "© Hardian Suprapto",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    informationDialog?.let { information ->
        val title = when (information) {
            SettingsInformation.PRIVACY -> "Privacy Policy"
            SettingsInformation.ABOUT -> "About RoamAtlas"
            SettingsInformation.HELP -> "Help & Feedback"
        }
        val message = when (information) {
            SettingsInformation.PRIVACY ->
                "Your Timeline locations, counters, manual choices, and imported flight details " +
                    "are stored in RoamAtlas on this device. RoamAtlas does not upload your " +
                    "Timeline to a RoamAtlas server.\n\n" +
                    "If you choose Gmail import, RoamAtlas asks Google for read-only Gmail " +
                    "permission and searches booking messages through Google. Imported flight " +
                    "details are then saved locally. RoamAtlas cannot send, edit, or delete email.\n\n" +
                    "Google Maps tiles, Gmail, and any airline-status page require internet access " +
                    "and are governed by those providers’ policies. Encrypted backups are written " +
                    "only to the file location you choose. Android may also include RoamAtlas data " +
                    "in your phone’s device backup when Android backup is enabled; that is controlled " +
                    "from your phone’s system backup settings. You can disconnect Gmail at any time."
            SettingsInformation.ABOUT ->
                "RoamAtlas ${BuildConfig.VERSION_NAME}\n\n" +
                    "A local-first personal travel-history app. It turns your Google Timeline or " +
                    "phone GPS records into transparent country, city, map, day, and home-versus-" +
                    "abroad summaries. You remain in control of manual day, flight, gate, and " +
                    "terminal corrections.\n\n" +
                    "Country and city totals reflect only dates that contain usable location records."
            SettingsInformation.HELP ->
                "If a number looks wrong, open Days to inspect the location evidence and correct " +
                    "individual dates. Tap a country or city to inspect its recorded dates and map.\n\n" +
                    "For Gmail error code 10, the app’s Google connection is not configured for this " +
                    "signed APK. This is an app setup problem, not a problem with your Gmail password."
        }
        AlertDialog(
            onDismissRequest = { informationDialog = null },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(message)
                }
            },
            confirmButton = {
                TextButton(onClick = { informationDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
