package com.example.speedreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1,
    val totalWordsRead: Int = 0,
    val todayWords: Int = 0,
    val lastReadDate: String = "",
    val streak: Int = 0,
    val streakUpdatedDate: String = "",
    val yesterdayWords: Int = 0
)
