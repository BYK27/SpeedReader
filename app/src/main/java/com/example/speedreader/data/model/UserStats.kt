package com.example.speedreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 0,
    val totalWordsRead: Long = 0L,
    val streakCount: Int = 0,
    val lastStreakDate: String = "" // format: yyyy-MM-dd
)