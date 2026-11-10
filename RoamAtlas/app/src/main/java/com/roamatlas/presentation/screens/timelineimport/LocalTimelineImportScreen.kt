package com.roamatlas.presentation.screens.timelineimport

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var currentStep by remember { mutableIntStateOf(1) }
    
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.importFromFolder(it) }
    }
    
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Your Timeline") },
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
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Import Your Past Locations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Follow these simple steps to get your Google location history into RoamAtlas",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Processing state
            if (uiState.isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Reading your timeline data...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "This may take a moment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Result state
            uiState.result?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.success) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (result.success) "Import Successful!" else "Import Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (result.success) {
                                    Text(
                                        "${result.locationsImported} locations imported",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (result.placesFound > 0) {
                                        Text(
                                            "${result.placesFound} places found",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    Text(
                                        result.message,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (result.success) {
                                Button(onClick = onNavigateBack) {
                                    Text("View Dashboard")
                                }
                            }
                            TextButton(onClick = { viewModel.clearResult() }) {
                                Text("Import More")
                            }
                        }
                    }
                }
            }
            
            // Step-by-step guide (only show when not processing and no result)
            if (!uiState.isProcessing && uiState.result == null) {
                // STEP 1
                StepCard(
                    stepNumber = 1,
                    title = "Open Google Takeout",
                    isExpanded = currentStep == 1,
                    onToggle = { currentStep = if (currentStep == 1) 0 else 1 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Google Takeout is a free service that lets you download your data from Google.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://takeout.google.com"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Google Takeout")
                        }
                        
                        Text(
                            "This will open in your browser. Sign in with your Google account if asked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // STEP 2
                StepCard(
                    stepNumber = 2,
                    title = "Select Location History",
                    isExpanded = currentStep == 2,
                    onToggle = { currentStep = if (currentStep == 2) 0 else 2 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InstructionItem("1. Click \"Deselect all\" at the top")
                        InstructionItem("2. Scroll down to find \"Location History (Timeline)\"")
                        InstructionItem("3. Check the box next to it")
                        InstructionItem("4. Click \"All Location History data included\" to expand")
                        InstructionItem("5. Make sure \"JSON format\" is selected (not KML)")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "JSON format is important! It contains all your location details.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // STEP 3
                StepCard(
                    stepNumber = 3,
                    title = "Download Your Data",
                    isExpanded = currentStep == 3,
                    onToggle = { currentStep = if (currentStep == 3) 0 else 3 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InstructionItem("1. Scroll to the bottom and click \"Next step\"")
                        InstructionItem("2. Choose \"Send download link via email\"")
                        InstructionItem("3. Select \".zip\" as the file type")
                        InstructionItem("4. Click \"Create export\"")
                        InstructionItem("5. Wait for the email (usually takes a few minutes)")
                        InstructionItem("6. Open the email and download the ZIP file")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "The export might take 5-30 minutes depending on how much data you have.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // STEP 4
                StepCard(
                    stepNumber = 4,
                    title = "Extract the ZIP File",
                    isExpanded = currentStep == 4,
                    onToggle = { currentStep = if (currentStep == 4) 0 else 4 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "After downloading, you need to extract (unzip) the file:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        InstructionItem("1. Open your Downloads folder")
                        InstructionItem("2. Find the file named \"takeout-XXXXXXXX.zip\"")
                        InstructionItem("3. Long press the ZIP file")
                        InstructionItem("4. Select \"Extract\" or \"Extract here\"")
                        InstructionItem("5. A new folder will be created with your data")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Your location data will be in:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                "Takeout → Location History (Timeline) → Semantic Location History",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // STEP 5 - IMPORT
                StepCard(
                    stepNumber = 5,
                    title = "Import into RoamAtlas",
                    isExpanded = currentStep == 5,
                    onToggle = { currentStep = if (currentStep == 5) 0 else 5 },
                    highlight = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Now you can import your location history!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select the Extracted Folder")
                        }
                        
                        Text(
                            "Navigate to: Downloads → Takeout → Location History",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            "Or import a single file:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        OutlinedButton(
                            onClick = { fileLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select a JSON File")
                        }
                    }
                }
                
                // Help card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Help,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Need Help?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• This imports ALL your past locations from Google Maps\n" +
                            "• It may include years of travel history\n" +
                            "• Your data stays on your phone - nothing is uploaded\n" +
                            "• You can import multiple times without duplicates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight && isExpanded) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (highlight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$stepNumber",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun InstructionItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
