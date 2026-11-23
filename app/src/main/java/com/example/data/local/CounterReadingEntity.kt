package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReadingEventType {
    INITIAL,
    CHANGED,
    ALERT,
    SNAPSHOT
}

@Entity(tableName = "counter_readings")
data class CounterReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val selector: String,
    val label: String,
    val rawValue: String,
    val numericValue: Long?,
    val previousValue: String?,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis()
)
