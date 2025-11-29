package com.example.speedreader.data.db

import androidx.room.*
import com.example.speedreader.data.model.UserStats

@Dao
interface UserStatsDao
{
    @Query("SELECT * FROM user_stats WHERE id = 0")
    suspend fun getStats(): UserStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: UserStats)
}