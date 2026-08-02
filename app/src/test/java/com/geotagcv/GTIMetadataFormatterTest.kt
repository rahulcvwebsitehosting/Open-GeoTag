package com.geotagcv

import com.dangiashish.GTIMetadataFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GTIMetadataFormatterTest {
    @Test
    fun liveLocation_isRenderedWithPlaceAddressAndCoordinates() {
        val lines = GTIMetadataFormatter.buildElements(
            place = "Chennai, India",
            address = "T. Nagar, Chennai, Tamil Nadu",
            latitude = 13.0418,
            longitude = 80.2341,
            showCoordinates = true,
            dateText = "2026-08-02 15:30",
            authorText = null,
            appText = null
        )

        assertEquals("Chennai, India", lines[0])
        assertEquals("T. Nagar, Chennai, Tamil Nadu", lines[1])
        assertTrue(lines[2].contains("13.0418"))
        assertTrue(lines[2].contains("80.2341"))
    }

    @Test
    fun coordinates_stillRenderWhenReverseGeocodingHasNoAddress() {
        val lines = GTIMetadataFormatter.buildElements(
            place = "",
            address = "",
            latitude = 28.6139,
            longitude = 77.2090,
            showCoordinates = true,
            dateText = null,
            authorText = null,
            appText = null
        )

        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("Lat Long :"))
    }

    @Test
    fun editedPlaceAndTime_areRenderedAsEntered() {
        val lines = GTIMetadataFormatter.buildElements(
            place = "Edited place",
            address = "Edited address",
            latitude = 19.076,
            longitude = 72.8777,
            showCoordinates = true,
            dateText = "04 Aug 2026 09:45 AM",
            authorText = null,
            appText = null
        )

        assertEquals("Edited place", lines[0])
        assertEquals("Edited address", lines[1])
        assertEquals("04 Aug 2026 09:45 AM", lines.last())
    }
}
