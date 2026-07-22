package com.hardian.phonetracker.data

data class UserProfile(
    val displayName: String = "",
    val countryCode: String = "+1",
    val phoneNumber: String = "",
) {
    val e164: String
        get() = normalizePhone(countryCode, phoneNumber)

    val isComplete: Boolean
        get() = displayName.isNotBlank() &&
            countryCode.isNotBlank() &&
            phoneNumber.filter { it.isDigit() }.length >= 7
}

data class TrackedContact(
    val id: String,
    val name: String,
    val countryCode: String,
    val phoneNumber: String,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastUpdatedMs: Long? = null,
) {
    val e164: String
        get() = normalizePhone(countryCode, phoneNumber)
}

data class LiveLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val updatedAtMs: Long,
)

fun normalizePhone(countryCode: String, phoneNumber: String): String {
    val code = countryCode.trim().let { if (it.startsWith("+")) it else "+$it" }
        .filter { it == '+' || it.isDigit() }
    val digits = phoneNumber.filter { it.isDigit() }
    return code + digits
}

val COMMON_COUNTRY_CODES = listOf(
    "+1" to "US/CA",
    "+44" to "UK",
    "+61" to "AU",
    "+62" to "ID",
    "+63" to "PH",
    "+65" to "SG",
    "+81" to "JP",
    "+82" to "KR",
    "+84" to "VN",
    "+86" to "CN",
    "+91" to "IN",
    "+966" to "SA",
)
