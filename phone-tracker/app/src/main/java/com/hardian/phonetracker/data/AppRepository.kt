package com.hardian.phonetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "phone_tracker")

class AppRepository(private val context: Context) {

    private val keyProfile = stringPreferencesKey("profile_json")
    private val keyContacts = stringPreferencesKey("contacts_json")

    private val _liveLocation = MutableStateFlow<LiveLocation?>(null)
    val liveLocation: StateFlow<LiveLocation?> = _liveLocation.asStateFlow()

    private val _sharingEnabled = MutableStateFlow(false)
    val sharingEnabled: StateFlow<Boolean> = _sharingEnabled.asStateFlow()

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        prefs[keyProfile]?.let { decodeProfile(it) } ?: UserProfile()
    }

    val contacts: Flow<List<TrackedContact>> = context.dataStore.data.map { prefs ->
        prefs[keyContacts]?.let { decodeContacts(it) } ?: emptyList()
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[keyProfile] = encodeProfile(profile)
        }
    }

    suspend fun addContact(name: String, countryCode: String, phoneNumber: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyContacts]?.let { decodeContacts(it) }?.toMutableList()
                ?: mutableListOf()
            val e164 = normalizePhone(countryCode, phoneNumber)
            if (current.any { it.e164 == e164 }) return@edit
            current.add(
                TrackedContact(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { e164 },
                    countryCode = countryCode.trim().let { if (it.startsWith("+")) it else "+$it" },
                    phoneNumber = phoneNumber.filter { it.isDigit() },
                ),
            )
            prefs[keyContacts] = encodeContacts(current)
        }
    }

    suspend fun removeContact(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyContacts]?.let { decodeContacts(it) }.orEmpty()
            prefs[keyContacts] = encodeContacts(current.filterNot { it.id == id })
        }
    }

    suspend fun updateContactLocation(
        id: String,
        latitude: Double,
        longitude: Double,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyContacts]?.let { decodeContacts(it) }.orEmpty()
            val updated = current.map {
                if (it.id == id) {
                    it.copy(
                        lastLatitude = latitude,
                        lastLongitude = longitude,
                        lastUpdatedMs = updatedAtMs,
                    )
                } else {
                    it
                }
            }
            prefs[keyContacts] = encodeContacts(updated)
        }
    }

    fun publishLiveLocation(location: LiveLocation) {
        _liveLocation.value = location
    }

    fun setSharingEnabled(enabled: Boolean) {
        _sharingEnabled.value = enabled
    }

    private fun encodeProfile(profile: UserProfile): String =
        JSONObject()
            .put("displayName", profile.displayName)
            .put("countryCode", profile.countryCode)
            .put("phoneNumber", profile.phoneNumber)
            .toString()

    private fun decodeProfile(raw: String): UserProfile {
        val json = JSONObject(raw)
        return UserProfile(
            displayName = json.optString("displayName"),
            countryCode = json.optString("countryCode", "+1"),
            phoneNumber = json.optString("phoneNumber"),
        )
    }

    private fun encodeContacts(contacts: List<TrackedContact>): String {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject()
                    .put("id", contact.id)
                    .put("name", contact.name)
                    .put("countryCode", contact.countryCode)
                    .put("phoneNumber", contact.phoneNumber)
                    .put("lastLatitude", contact.lastLatitude ?: JSONObject.NULL)
                    .put("lastLongitude", contact.lastLongitude ?: JSONObject.NULL)
                    .put("lastUpdatedMs", contact.lastUpdatedMs ?: JSONObject.NULL),
            )
        }
        return array.toString()
    }

    private fun decodeContacts(raw: String): List<TrackedContact> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                add(
                    TrackedContact(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        countryCode = json.getString("countryCode"),
                        phoneNumber = json.getString("phoneNumber"),
                        lastLatitude = json.optDoubleOrNull("lastLatitude"),
                        lastLongitude = json.optDoubleOrNull("lastLongitude"),
                        lastUpdatedMs = json.optLongOrNull("lastUpdatedMs"),
                    ),
                )
            }
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
