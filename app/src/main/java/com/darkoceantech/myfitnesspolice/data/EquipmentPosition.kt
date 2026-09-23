package com.darkoceantech.myfitnesspolice.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/** A setup value belongs to a workout exercise, and is copied into each session snapshot. */
data class EquipmentPosition(val name: String, val position: String)

class EquipmentPositionConverters {
    @TypeConverter
    fun encode(positions: List<EquipmentPosition>): String = JSONArray().apply {
        positions.forEach { item -> put(JSONObject().put("name", item.name).put("position", item.position)) }
    }.toString()

    @TypeConverter
    fun decode(value: String): List<EquipmentPosition> {
        val array = JSONArray(value)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            EquipmentPosition(item.getString("name"), item.getString("position"))
        }
    }
}
