package com.hardian.phonetracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hardian.phonetracker.ui.screens.ContactsScreen
import com.hardian.phonetracker.ui.screens.MapScreen
import com.hardian.phonetracker.ui.screens.SetupScreen

private object Routes {
    const val Setup = "setup"
    const val Map = "map"
    const val Contacts = "contacts"
}

@Composable
fun PhoneTrackerRoot(vm: TrackerViewModel = viewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val liveLocation by vm.liveLocation.collectAsStateWithLifecycle()
    val sharingEnabled by vm.sharingEnabled.collectAsStateWithLifecycle()

    if (!profile.isComplete) {
        SetupScreen(
            initial = profile,
            onSave = vm::saveProfile,
        )
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Routes.Map

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == Routes.Map,
                    onClick = {
                        navController.navigate(Routes.Map) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Routes.Map) { saveState = true }
                        }
                    },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = current == Routes.Contacts,
                    onClick = {
                        navController.navigate(Routes.Contacts) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Routes.Map) { saveState = true }
                        }
                    },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Contacts") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Map,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Map) {
                MapScreen(
                    profile = profile,
                    liveLocation = liveLocation,
                    contacts = contacts,
                    sharingEnabled = sharingEnabled,
                    onStartSharing = vm::startSharing,
                    onStopSharing = vm::stopSharing,
                )
            }
            composable(Routes.Contacts) {
                ContactsScreen(
                    contacts = contacts,
                    canSimulate = liveLocation != null,
                    onAdd = vm::addContact,
                    onRemove = vm::removeContact,
                    onSimulateNearMe = vm::simulateContactNearMe,
                )
            }
        }
    }
}
