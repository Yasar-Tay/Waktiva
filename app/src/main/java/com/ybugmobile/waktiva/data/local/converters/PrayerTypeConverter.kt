package com.ybugmobile.waktiva.data.local.converters

import androidx.room.TypeConverter
import com.ybugmobile.waktiva.domain.model.PrayerType

class PrayerTypeConverter {
    @TypeConverter
    fun fromPrayerType(value: PrayerType): String {
        return value.name
    }

    @TypeConverter
    fun toPrayerType(value: String): PrayerType {
        return PrayerType.valueOf(value)
    }
}
