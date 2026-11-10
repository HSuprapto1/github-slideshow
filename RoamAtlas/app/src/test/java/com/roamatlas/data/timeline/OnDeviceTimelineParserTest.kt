package com.roamatlas.data.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceTimelineParserTest {

    @Test
    fun parsesAndroidVisitActivityPathAndSkipsRawWhenSegmentsExist() {
        val json = """
            {
              "semanticSegments": [
                {
                  "startTime": "2024-06-15T09:00:00.000+02:00",
                  "endTime": "2024-06-15T10:00:00.000+02:00",
                  "visit": {
                    "probability": 0.9,
                    "topCandidate": {
                      "semanticType": "UNKNOWN",
                      "placeLocation": { "latLng": "48.8566°, 2.3522°" }
                    }
                  }
                },
                {
                  "startTime": "2024-06-15T10:00:00.000Z",
                  "endTime": "2024-06-15T11:00:00.000Z",
                  "activity": {
                    "topCandidate": { "type": "IN_PASSENGER_VEHICLE" },
                    "start": { "latLng": "48.8566°, 2.3522°" },
                    "end": "geo:48.8600,2.3400"
                  },
                  "timelinePath": [
                    { "point": "48.8570°, 2.3500°", "time": "2024-06-15T10:15:00.000Z" },
                    { "point": "48.8580°, 2.3450°", "durationMinutesOffsetFromStartTime": 30 }
                  ]
                }
              ],
              "rawSignals": [
                { "position": { "LatLng": "1.0000, 2.0000", "timestamp": "2024-06-15T09:05:00.000Z" } }
              ]
            }
        """.trimIndent()

        val parsed = OnDeviceTimelineParser.parse(json)

        assertEquals(parsed.message, OnDeviceTimelineParser.Kind.ON_DEVICE, parsed.kind)
        assertEquals(1, parsed.places)
        assertEquals(5, parsed.points.size)
        assertEquals(48.8566, parsed.points[0].latitude, 0.0001)
        assertEquals(2.3522, parsed.points[0].longitude, 0.0001)
        assertTrue(parsed.points.none { it.latitude == 1.0 })
        assertTrue(parsed.message.contains("left out"))
    }

    @Test
    fun parsesRawSignalsWhenThereAreNoVisits() {
        val json = """
            {
              "rawSignals": [
                {
                  "position": {
                    "LatLng": "geo:48.833657,2.256223,35",
                    "accuracyMeters": 13,
                    "altitudeMeters": 90.7,
                    "speedMetersPerSecond": 0.07,
                    "timestamp": "2024-06-06T11:44:37.000+01:00",
                    "source": "WIFI"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = OnDeviceTimelineParser.parse(json)

        assertEquals(1, parsed.points.size)
        assertEquals(48.833657, parsed.points[0].latitude, 0.000001)
        assertEquals(2.256223, parsed.points[0].longitude, 0.000001)
        assertEquals(13f, parsed.points[0].accuracy)
        assertEquals("VERIFIED", parsed.points[0].confidence)
    }

    @Test
    fun parsesIosSegmentArray() {
        val json = """
            [
              {
                "startTime": "2025-01-02T08:00:00Z",
                "visit": { "topCandidate": { "placeLocation": "geo:52.5200,13.4050" } }
              }
            ]
        """.trimIndent()

        val parsed = OnDeviceTimelineParser.parse(json)

        assertEquals(OnDeviceTimelineParser.Kind.ON_DEVICE, parsed.kind)
        assertEquals(52.5200, parsed.points.single().latitude, 0.0001)
        assertEquals(13.4050, parsed.points.single().longitude, 0.0001)
    }

    @Test
    fun rejectsEncryptedTakeoutText() {
        val parsed = OnDeviceTimelineParser.parse("Encrypted Backups\nThis backup cannot be read.")

        assertEquals(OnDeviceTimelineParser.Kind.ENCRYPTED, parsed.kind)
        assertTrue(parsed.points.isEmpty())
        assertTrue(parsed.message.contains("Settings"))
    }

    @Test
    fun leavesOldTakeoutFormatToTheOtherParser() {
        val json = """{ "locations": [ { "latitudeE7": 1, "longitudeE7": 2 } ] }"""
        val parsed = OnDeviceTimelineParser.parse(json)
        assertEquals(OnDeviceTimelineParser.Kind.NOT_THIS_FORMAT, parsed.kind)
    }
}
