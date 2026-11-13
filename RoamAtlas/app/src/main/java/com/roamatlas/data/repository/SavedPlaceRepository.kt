package com.roamatlas.data.repository

import android.net.Uri
import com.roamatlas.data.local.dao.UserSettingsDao
import com.roamatlas.data.local.entity.UserSettingsEntity
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedPlaceRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao
) {
    suspend fun getLabel(countryCode: String, detectedName: String): String? =
        userSettingsDao.getSetting(labelKey(countryCode, detectedName))

    suspend fun getPhotoUris(countryCode: String, detectedName: String): List<String> =
        userSettingsDao.getSetting(photoKey(countryCode, detectedName))
            ?.let(::decodePhotoUris)
            .orEmpty()

    suspend fun saveLabel(countryCode: String, detectedName: String, label: String) {
        val key = labelKey(countryCode, detectedName)
        if (label.isBlank()) {
            userSettingsDao.deleteSetting(key)
        } else {
            userSettingsDao.setSetting(UserSettingsEntity(key, label.trim()))
        }
    }

    suspend fun savePhotoUris(
        countryCode: String,
        detectedName: String,
        uris: List<String>
    ) {
        val key = photoKey(countryCode, detectedName)
        val uniqueUris = uris.filter { it.isNotBlank() }.distinct()
        if (uniqueUris.isEmpty()) {
            userSettingsDao.deleteSetting(key)
        } else {
            userSettingsDao.setSetting(
                UserSettingsEntity(key, JSONArray(uniqueUris).toString())
            )
        }
    }

    suspend fun getAllLabels(): Map<String, String> =
        userSettingsDao.getSettingsWithPrefix(LABEL_PREFIX).mapNotNull { setting ->
            val parts = setting.key.removePrefix(LABEL_PREFIX).split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            placeId(parts[0], Uri.decode(parts[1])) to setting.value
        }.toMap()

    suspend fun getAllPhotoCounts(): Map<String, Int> =
        userSettingsDao.getSettingsWithPrefix(PHOTO_PREFIX).mapNotNull { setting ->
            val parts = setting.key.removePrefix(PHOTO_PREFIX).split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            placeId(parts[0], Uri.decode(parts[1])) to decodePhotoUris(setting.value).size
        }.toMap()

    companion object {
        private const val LABEL_PREFIX = "saved_place_label|"
        private const val PHOTO_PREFIX = "saved_place_photos|"

        fun placeId(countryCode: String, detectedName: String): String =
            "${countryCode.uppercase()}:${detectedName.trim().lowercase()}"

        private fun labelKey(countryCode: String, detectedName: String): String =
            "$LABEL_PREFIX${countryCode.uppercase()}|${Uri.encode(detectedName)}"

        private fun photoKey(countryCode: String, detectedName: String): String =
            "$PHOTO_PREFIX${countryCode.uppercase()}|${Uri.encode(detectedName)}"

        private fun decodePhotoUris(json: String): List<String> = runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}
