package com.tds.binarystars.model

/**
 * Location history point for map display.
 */
data class LocationHistoryPoint(
    val id: String,
    val title: String,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double
)
