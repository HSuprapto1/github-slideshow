package com.hardian.phonetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hardian.phonetracker.data.COMMON_COUNTRY_CODES
import com.hardian.phonetracker.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initial: UserProfile,
    onSave: (displayName: String, countryCode: String, phoneNumber: String) -> Unit,
) {
    var displayName by remember(initial) { mutableStateOf(initial.displayName) }
    var countryCode by remember(initial) { mutableStateOf(initial.countryCode.ifBlank { "+1" }) }
    var phoneNumber by remember(initial) { mutableStateOf(initial.phoneNumber) }
    var menuOpen by remember { mutableStateOf(false) }

    val canContinue = displayName.isNotBlank() &&
        countryCode.isNotBlank() &&
        phoneNumber.filter { it.isDigit() }.length >= 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Phone Tracker",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Enter your country code and mobile number. This phone can share its live GPS — you cannot locate strangers by number alone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                modifier = Modifier.width(130.dp),
            ) {
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it },
                    label = { Text("Code") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    modifier = Modifier.menuAnchor(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                ExposedDropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    COMMON_COUNTRY_CODES.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text("$code  $label") },
                            onClick = {
                                countryCode = code
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                label = { Text("Mobile number") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }

        Text(
            text = "Example: +62 81234567890 (Indonesia) or +1 5551234567",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSave(displayName, countryCode, phoneNumber) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}
