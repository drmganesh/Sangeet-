package com.example.sangeet.data.local

import androidx.room.TypeConverter
import com.example.sangeet.data.model.RuleCreationDate
import com.example.sangeet.data.model.RuleListKind
import com.example.sangeet.data.model.RuleStatus
import com.example.sangeet.data.model.RuleType

class Converters {
    @TypeConverter
    fun fromRuleType(value: RuleType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): RuleType = RuleType.fromString(value)

    @TypeConverter
    fun fromRuleListKind(value: RuleListKind): String = value.name

    @TypeConverter
    fun toRuleListKind(value: String): RuleListKind =
        RuleListKind.entries.find { it.name == value } ?: RuleListKind.BLACKLIST

    @TypeConverter
    fun fromRuleStatus(value: RuleStatus): String = value.name

    @TypeConverter
    fun toRuleStatus(value: String): RuleStatus = RuleStatus.fromString(value)

    @TypeConverter
    fun fromRuleCreationDate(date: RuleCreationDate): Long = date.epochMillis

    @TypeConverter
    fun toRuleCreationDate(millis: Long): RuleCreationDate = RuleCreationDate(millis)
}
