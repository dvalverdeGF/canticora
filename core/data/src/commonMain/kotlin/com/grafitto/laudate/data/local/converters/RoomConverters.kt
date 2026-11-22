package com.grafitto.laudate.data.local.converters

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Conversores de tipos para serialización de listas JSON en columnas Room.
 */
class RoomConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromListString(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toListString(value: String): List<String> = try {
        json.decodeFromString(value)
    } catch (_: Exception) {
        emptyList()
    }
}
