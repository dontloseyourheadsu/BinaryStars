package com.tds.binarystars.model

import com.tds.binarystars.api.DeviceTypeDto

/**
 * Map list item representing a device and its online state.
 */
data class MapDeviceItem(
    val id: String,
    val name: String,
    val type: DeviceTypeDto,
    val isOnline: Boolean,
    val isCurrent: Boolean
)
