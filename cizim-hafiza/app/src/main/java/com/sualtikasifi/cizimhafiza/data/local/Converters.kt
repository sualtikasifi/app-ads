package com.sualtikasifi.cizimhafiza.data.local

import androidx.room.TypeConverter
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty

class Converters {
    @TypeConverter
    fun fromDifficulty(value: Difficulty): String = value.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)
}
