package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {
    @Query("SELECT * FROM counter_readings ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentReadings(): Flow<List<CounterReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: CounterReadingEntity): Long

    @Query("DELETE FROM counter_readings")
    suspend fun clearAll()
}
