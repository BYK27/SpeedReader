package com.example.speedreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.speedreader.data.model.PdfBook
import com.example.speedreader.data.model.UserStats

@Database(entities = [PdfBook::class, UserStats::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfBookDao(): PdfBookDao
    abstract fun userStatsDao(): UserStatsDao
}