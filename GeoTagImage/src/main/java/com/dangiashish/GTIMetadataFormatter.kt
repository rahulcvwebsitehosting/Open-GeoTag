package com.dangiashish

object GTIMetadataFormatter {
    fun buildElements(
        place: String,
        address: String,
        latitude: Double,
        longitude: Double,
        showCoordinates: Boolean,
        dateText: String?,
        authorText: String?,
        appText: String?
    ): List<String> = buildList {
        val hasCoordinates = latitude != 0.0 || longitude != 0.0
        place.trim().takeIf(String::isNotEmpty)?.let(::add)
        address.trim().takeIf(String::isNotEmpty)?.let(::add)
        if (showCoordinates && hasCoordinates) {
            add("Lat Long : $latitude, $longitude")
        }
        dateText?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        authorText?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        appText?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }
}
