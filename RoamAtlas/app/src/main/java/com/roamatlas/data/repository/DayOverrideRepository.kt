package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.UserSettingsDao
import com.roamatlas.data.local.entity.UserSettingsEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ManualDayClassification {
    INSIDE,
    OUTSIDE
}

@Singleton
class DayOverrideRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao
) {
    suspend fun getOverrides(countryCode: String): Map<LocalDate, ManualDayClassification> {
        val prefix = prefix(countryCode)
        return userSettingsDao.getSettingsWithPrefix(prefix).mapNotNull { setting ->
            val date = setting.key.removePrefix(prefix).toLocalDateOrNull()
                ?: return@mapNotNull null
            val classification = runCatching {
                ManualDayClassification.valueOf(setting.value)
            }.getOrNull() ?: return@mapNotNull null
            date to classification
        }.toMap()
    }

    suspend fun setOverride(
        countryCode: String,
        date: LocalDate,
        classification: ManualDayClassification
    ) {
        userSettingsDao.setSetting(
            UserSettingsEntity(key(countryCode, date), classification.name)
        )
    }

    suspend fun removeOverride(countryCode: String, date: LocalDate) {
        userSettingsDao.deleteSetting(key(countryCode, date))
    }

    private fun prefix(countryCode: String) =
        "$KEY_PREFIX${countryCode.uppercase()}:"

    private fun key(countryCode: String, date: LocalDate) =
        "${prefix(countryCode)}$date"

    private fun String.toLocalDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val KEY_PREFIX = "day_override:"
    }
}
