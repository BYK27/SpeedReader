package com.example.speedreader.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_books")
data class PdfBook(
    @PrimaryKey val uri: String,
    val name: String,
    val lastWordIndex: Int = 0 // <-- store reading progress
)