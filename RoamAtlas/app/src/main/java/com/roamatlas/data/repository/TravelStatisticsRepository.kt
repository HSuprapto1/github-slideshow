package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.dao.UserSettingsDao
import com.roamatlas.data.local.entity.UserSettingsEntity
import com.roamatlas.domain.model.CountryVisitStats
import com.roamatlas.domain.model.CountryVisitClassification
import com.roamatlas.domain.model.TravelStatistics
import com.roamatlas.domain.model.YearlyTravelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TravelStatisticsRepository @Inject constructor(
    private val locationDao: LocationDao,
    private val flightDao: FlightDao,
    private val userSettingsDao: UserSettingsDao
) {
    
    suspend fun getHomeCountryCode(): String? {
        return userSettingsDao.getSetting(UserSettingsEntity.KEY_HOME_COUNTRY_CODE)
    }
    
    fun getHomeCountryCodeFlow(): Flow<String?> {
        return userSettingsDao.getSettingFlow(UserSettingsEntity.KEY_HOME_COUNTRY_CODE)
    }
    
    suspend fun setHomeCountry(countryCode: String, countryName: String) {
        userSettingsDao.setSetting(
            UserSettingsEntity(UserSettingsEntity.KEY_HOME_COUNTRY_CODE, countryCode)
        )
        userSettingsDao.setSetting(
            UserSettingsEntity(UserSettingsEntity.KEY_HOME_COUNTRY_NAME, countryName)
        )
    }
    
    fun getTravelStatistics(): Flow<TravelStatistics> =
        combine(
            locationDao.getLocationCount(),
            flightDao.getFlightCount(),
            getHomeCountryCodeFlow()
        ) { locationCount, flightCount, homeCountryCode ->
            Triple(locationCount, flightCount, homeCountryCode)
        }.mapLatest { (locationCount, flightCount, homeCountryCode) ->
        val countrySummaries = locationDao.getCountryVisitSummaries()
        val earliestTimestamp = locationDao.getEarliestTimestamp()
        val latestTimestamp = locationDao.getLatestTimestamp()
        
        val totalFlightDistance = flightDao.getTotalFlightDistance() ?: 0.0
        val totalFlightDuration = flightDao.getTotalFlightDuration() ?: 0
        val longestFlight = flightDao.getLongestFlight()
        
        val flights = flightDao.getAllFlights().first().sortedBy { it.departureTimeMillis }
        val countryStats = countrySummaries.map { summary ->
            val classification = classifyCountry(summary.countryCode, summary.daysSpent, flights)
            CountryVisitStats(
                countryCode = summary.countryCode,
                countryName = summary.countryName ?: getCountryName(summary.countryCode),
                daysSpent = summary.daysSpent,
                visitCount = summary.visitCount,
                firstVisit = Instant.ofEpochMilli(summary.firstTimestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate(),
                lastVisit = Instant.ofEpochMilli(summary.lastTimestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate(),
                classification = classification.first,
                classificationReason = classification.second
            )
        }
        val mostVisited = countryStats
            .filter { it.classification != CountryVisitClassification.TRANSIT }
            .maxByOrNull { it.daysSpent }
        
        val (daysInside, daysOutside) = if (homeCountryCode != null) {
            calculateDaysInsideOutside(homeCountryCode)
        } else {
            Pair(0, 0)
        }
        
        val yearlyStats = calculateYearlyStats()
        
        TravelStatistics(
            totalCountriesVisited = countryStats.count {
                it.classification != CountryVisitClassification.TRANSIT
            },
            totalCitiesVisited = locationDao.countDistinctCities(),
            totalLocationsRecorded = locationCount,
            totalFlights = flightCount,
            totalFlightDistanceKm = totalFlightDistance,
            totalFlightDurationMinutes = totalFlightDuration,
            longestFlightKm = longestFlight?.distanceKm,
            longestFlightRoute = longestFlight?.let { 
                "${it.departureAirportCode} → ${it.arrivalAirportCode}" 
            },
            mostVisitedCountry = mostVisited?.countryName,
            mostVisitedCountryDays = mostVisited?.daysSpent ?: 0,
            daysOutsideHomeCountry = daysOutside,
            daysInsideHomeCountry = daysInside,
            totalDaysTracked = daysInside + daysOutside,
            earliestRecordDate = earliestTimestamp?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            },
            latestRecordDate = latestTimestamp?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            },
            countriesVisitedList = countryStats,
            yearlyStats = yearlyStats
        )
    }

    private suspend fun classifyCountry(
        countryCode: String,
        daysSpent: Int,
        flights: List<com.roamatlas.data.local.entity.FlightEntity>
    ): Pair<CountryVisitClassification, String?> {
        for (index in 0 until flights.lastIndex) {
            val arriving = flights[index]
            val onward = flights[index + 1]
            val layoverMillis = onward.departureTimeMillis - arriving.arrivalTimeMillis
            if (layoverMillis !in 0..TimeUnit.HOURS.toMillis(24)) continue
            val arrivalCountry = arriving.arrivalCountryCode
                ?: locationDao.getDominantCountryBetween(
                    arriving.arrivalTimeMillis - TimeUnit.HOURS.toMillis(2),
                    arriving.arrivalTimeMillis + TimeUnit.HOURS.toMillis(6)
                )
            val onwardCountry = onward.departureCountryCode
                ?: locationDao.getDominantCountryBetween(
                    onward.departureTimeMillis - TimeUnit.HOURS.toMillis(6),
                    onward.departureTimeMillis + TimeUnit.HOURS.toMillis(2)
                )
            if (arrivalCountry != countryCode || onwardCountry != countryCode) continue

            val hours = TimeUnit.MILLISECONDS.toHours(layoverMillis)
            val sameAirport = arriving.arrivalAirportCode.equals(
                onward.departureAirportCode,
                ignoreCase = true
            )
            return when {
                sameAirport && hours <= 12 && daysSpent <= 1 ->
                    CountryVisitClassification.TRANSIT to
                        "Connecting flight at ${arriving.arrivalAirportCode} after ${hours}h"
                daysSpent <= 1 ->
                    CountryVisitClassification.UNCERTAIN to
                        "Possible ${hours}h connection; verify whether you entered the country"
                else -> CountryVisitClassification.VISITED to null
            }
        }
        return CountryVisitClassification.VISITED to null
    }
    
    private suspend fun calculateDaysInsideOutside(homeCountryCode: String): Pair<Int, Int> {
        val earliest = locationDao.getEarliestTimestamp() ?: return Pair(0, 0)
        val latest = locationDao.getLatestTimestamp() ?: return Pair(0, 0)
        val daysInHome = locationDao.countDaysInCountry(homeCountryCode, earliest, latest)
        val totalTrackedDays = locationDao.countTrackedDays()
        val daysOutside = totalTrackedDays - daysInHome
        
        return Pair(daysInHome, maxOf(0, daysOutside))
    }
    
    private suspend fun calculateYearlyStats(): List<YearlyTravelStats> {
        val flightStats = flightDao.getYearlyFlightStats().associateBy { it.year }
        val locationStats = locationDao.getYearlyLocationSummaries().associateBy { it.year }
        return (flightStats.keys + locationStats.keys)
            .mapNotNull { it.toIntOrNull() }
            .sortedDescending()
            .map { year ->
            val flight = flightStats[year.toString()]
            val location = locationStats[year.toString()]
            YearlyTravelStats(
                year = year,
                countriesVisited = location?.countriesVisited ?: 0,
                daysTracked = location?.daysTracked ?: 0,
                flights = flight?.flightCount ?: 0,
                flightDistanceKm = flight?.totalDistance ?: 0.0
            )
        }
    }
    
    private fun getCountryName(code: String): String {
        return COUNTRY_NAMES[code] ?: code
    }
    
    companion object {
        private val COUNTRY_NAMES = mapOf(
            "AD" to "Andorra", "AE" to "United Arab Emirates", "AF" to "Afghanistan",
            "AG" to "Antigua and Barbuda", "AI" to "Anguilla", "AL" to "Albania",
            "AM" to "Armenia", "AO" to "Angola", "AQ" to "Antarctica",
            "AR" to "Argentina", "AS" to "American Samoa", "AT" to "Austria",
            "AU" to "Australia", "AW" to "Aruba", "AX" to "Åland Islands",
            "AZ" to "Azerbaijan", "BA" to "Bosnia and Herzegovina", "BB" to "Barbados",
            "BD" to "Bangladesh", "BE" to "Belgium", "BF" to "Burkina Faso",
            "BG" to "Bulgaria", "BH" to "Bahrain", "BI" to "Burundi",
            "BJ" to "Benin", "BL" to "Saint Barthélemy", "BM" to "Bermuda",
            "BN" to "Brunei", "BO" to "Bolivia", "BQ" to "Caribbean Netherlands",
            "BR" to "Brazil", "BS" to "Bahamas", "BT" to "Bhutan",
            "BW" to "Botswana", "BY" to "Belarus", "BZ" to "Belize",
            "CA" to "Canada", "CC" to "Cocos Islands", "CD" to "DR Congo",
            "CF" to "Central African Republic", "CG" to "Congo", "CH" to "Switzerland",
            "CI" to "Côte d'Ivoire", "CK" to "Cook Islands", "CL" to "Chile",
            "CM" to "Cameroon", "CN" to "China", "CO" to "Colombia",
            "CR" to "Costa Rica", "CU" to "Cuba", "CV" to "Cape Verde",
            "CW" to "Curaçao", "CX" to "Christmas Island", "CY" to "Cyprus",
            "CZ" to "Czechia", "DE" to "Germany", "DJ" to "Djibouti",
            "DK" to "Denmark", "DM" to "Dominica", "DO" to "Dominican Republic",
            "DZ" to "Algeria", "EC" to "Ecuador", "EE" to "Estonia",
            "EG" to "Egypt", "EH" to "Western Sahara", "ER" to "Eritrea",
            "ES" to "Spain", "ET" to "Ethiopia", "FI" to "Finland",
            "FJ" to "Fiji", "FK" to "Falkland Islands", "FM" to "Micronesia",
            "FO" to "Faroe Islands", "FR" to "France", "GA" to "Gabon",
            "GB" to "United Kingdom", "GD" to "Grenada", "GE" to "Georgia",
            "GF" to "French Guiana", "GG" to "Guernsey", "GH" to "Ghana",
            "GI" to "Gibraltar", "GL" to "Greenland", "GM" to "Gambia",
            "GN" to "Guinea", "GP" to "Guadeloupe", "GQ" to "Equatorial Guinea",
            "GR" to "Greece", "GT" to "Guatemala", "GU" to "Guam",
            "GW" to "Guinea-Bissau", "GY" to "Guyana", "HK" to "Hong Kong",
            "HN" to "Honduras", "HR" to "Croatia", "HT" to "Haiti",
            "HU" to "Hungary", "ID" to "Indonesia", "IE" to "Ireland",
            "IL" to "Israel", "IM" to "Isle of Man", "IN" to "India",
            "IO" to "British Indian Ocean Territory", "IQ" to "Iraq", "IR" to "Iran",
            "IS" to "Iceland", "IT" to "Italy", "JE" to "Jersey",
            "JM" to "Jamaica", "JO" to "Jordan", "JP" to "Japan",
            "KE" to "Kenya", "KG" to "Kyrgyzstan", "KH" to "Cambodia",
            "KI" to "Kiribati", "KM" to "Comoros", "KN" to "Saint Kitts and Nevis",
            "KP" to "North Korea", "KR" to "South Korea", "KW" to "Kuwait",
            "KY" to "Cayman Islands", "KZ" to "Kazakhstan", "LA" to "Laos",
            "LB" to "Lebanon", "LC" to "Saint Lucia", "LI" to "Liechtenstein",
            "LK" to "Sri Lanka", "LR" to "Liberia", "LS" to "Lesotho",
            "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia",
            "LY" to "Libya", "MA" to "Morocco", "MC" to "Monaco",
            "MD" to "Moldova", "ME" to "Montenegro", "MF" to "Saint Martin",
            "MG" to "Madagascar", "MH" to "Marshall Islands", "MK" to "North Macedonia",
            "ML" to "Mali", "MM" to "Myanmar", "MN" to "Mongolia",
            "MO" to "Macau", "MP" to "Northern Mariana Islands", "MQ" to "Martinique",
            "MR" to "Mauritania", "MS" to "Montserrat", "MT" to "Malta",
            "MU" to "Mauritius", "MV" to "Maldives", "MW" to "Malawi",
            "MX" to "Mexico", "MY" to "Malaysia", "MZ" to "Mozambique",
            "NA" to "Namibia", "NC" to "New Caledonia", "NE" to "Niger",
            "NF" to "Norfolk Island", "NG" to "Nigeria", "NI" to "Nicaragua",
            "NL" to "Netherlands", "NO" to "Norway", "NP" to "Nepal",
            "NR" to "Nauru", "NU" to "Niue", "NZ" to "New Zealand",
            "OM" to "Oman", "PA" to "Panama", "PE" to "Peru",
            "PF" to "French Polynesia", "PG" to "Papua New Guinea", "PH" to "Philippines",
            "PK" to "Pakistan", "PL" to "Poland", "PM" to "Saint Pierre and Miquelon",
            "PN" to "Pitcairn Islands", "PR" to "Puerto Rico", "PS" to "Palestine",
            "PT" to "Portugal", "PW" to "Palau", "PY" to "Paraguay",
            "QA" to "Qatar", "RE" to "Réunion", "RO" to "Romania",
            "RS" to "Serbia", "RU" to "Russia", "RW" to "Rwanda",
            "SA" to "Saudi Arabia", "SB" to "Solomon Islands", "SC" to "Seychelles",
            "SD" to "Sudan", "SE" to "Sweden", "SG" to "Singapore",
            "SH" to "Saint Helena", "SI" to "Slovenia", "SJ" to "Svalbard and Jan Mayen",
            "SK" to "Slovakia", "SL" to "Sierra Leone", "SM" to "San Marino",
            "SN" to "Senegal", "SO" to "Somalia", "SR" to "Suriname",
            "SS" to "South Sudan", "ST" to "São Tomé and Príncipe", "SV" to "El Salvador",
            "SX" to "Sint Maarten", "SY" to "Syria", "SZ" to "Eswatini",
            "TC" to "Turks and Caicos Islands", "TD" to "Chad", "TF" to "French Southern Territories",
            "TG" to "Togo", "TH" to "Thailand", "TJ" to "Tajikistan",
            "TK" to "Tokelau", "TL" to "Timor-Leste", "TM" to "Turkmenistan",
            "TN" to "Tunisia", "TO" to "Tonga", "TR" to "Turkey",
            "TT" to "Trinidad and Tobago", "TV" to "Tuvalu", "TW" to "Taiwan",
            "TZ" to "Tanzania", "UA" to "Ukraine", "UG" to "Uganda",
            "US" to "United States", "UY" to "Uruguay", "UZ" to "Uzbekistan",
            "VA" to "Vatican City", "VC" to "Saint Vincent and the Grenadines", "VE" to "Venezuela",
            "VG" to "British Virgin Islands", "VI" to "U.S. Virgin Islands", "VN" to "Vietnam",
            "VU" to "Vanuatu", "WF" to "Wallis and Futuna", "WS" to "Samoa",
            "XK" to "Kosovo", "YE" to "Yemen", "YT" to "Mayotte",
            "ZA" to "South Africa", "ZM" to "Zambia", "ZW" to "Zimbabwe"
        )
        
        fun getAllCountries(): List<Pair<String, String>> {
            return COUNTRY_NAMES.toList().sortedBy { it.second }
        }
    }
}
