package com.roamatlas.data.location

/**
 * Heuristic: phone GPS altitude/speed suggesting the user is flying,
 * so the point should be stored as flight path — not a country visit.
 *
 * Phone altitude is rough; rules are conservative to avoid marking mountain
 * cities as airborne unless speed also looks like aircraft.
 */
object AirborneClassifier {
    /** Clearly cruise altitude (WGS84 ellipsoidal meters). */
    private const val CLEAR_CRUISE_ALT_M = 6_000.0

    /** High enough that with fast speed it's unlikely ground travel. */
    private const val HIGH_ALT_M = 2_500.0

    /** ~144 km/h */
    private const val FAST_MS = 40f

    /** ~250 km/h — rare for cars/trains continuously via phone GPS */
    private const val VERY_FAST_MS = 70f

    fun isLikelyAirborne(altitudeMeters: Double?, speedMetersPerSecond: Float?): Boolean {
        val alt = altitudeMeters ?: return speedMetersPerSecond != null &&
            speedMetersPerSecond >= VERY_FAST_MS
        val speed = speedMetersPerSecond ?: 0f
        if (alt >= CLEAR_CRUISE_ALT_M) return true
        if (alt >= HIGH_ALT_M && speed >= FAST_MS) return true
        if (speed >= VERY_FAST_MS) return true
        return false
    }

    fun noteSuffix(isAirborne: Boolean): String? =
        if (isAirborne) "Airborne GPS (excluded from country visits)" else null
}
