package com.hardian.phonetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hardian.phonetracker.PhoneTrackerApp
import com.hardian.phonetracker.data.LiveLocation
import com.hardian.phonetracker.data.TrackedContact
import com.hardian.phonetracker.data.UserProfile
import com.hardian.phonetracker.location.LocationForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PhoneTrackerApp).repository

    val profile: StateFlow<UserProfile> = repository.profile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserProfile(),
    )

    val contacts: StateFlow<List<TrackedContact>> = repository.contacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val liveLocation: StateFlow<LiveLocation?> = repository.liveLocation
    val sharingEnabled: StateFlow<Boolean> = repository.sharingEnabled

    fun saveProfile(displayName: String, countryCode: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.saveProfile(
                UserProfile(
                    displayName = displayName.trim(),
                    countryCode = countryCode.trim(),
                    phoneNumber = phoneNumber.trim(),
                ),
            )
        }
    }

    fun addContact(name: String, countryCode: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.addContact(name, countryCode, phoneNumber)
        }
    }

    fun removeContact(id: String) {
        viewModelScope.launch {
            repository.removeContact(id)
        }
    }

    /** Demo helper: pins a contact near your current location so the map UI is testable. */
    fun simulateContactNearMe(contactId: String) {
        val me = liveLocation.value ?: return
        viewModelScope.launch {
            repository.updateContactLocation(
                id = contactId,
                latitude = me.latitude + 0.002,
                longitude = me.longitude + 0.0015,
            )
        }
    }

    fun startSharing() {
        LocationForegroundService.start(getApplication())
    }

    fun stopSharing() {
        LocationForegroundService.stop(getApplication())
    }
}
