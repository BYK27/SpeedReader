package com.example.speedreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.speedreader.data.model.PdfBook

@Database(entities = [PdfBook::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfBookDao(): PdfBookDao
}