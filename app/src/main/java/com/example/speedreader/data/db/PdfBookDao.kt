package com.example.speedreader.data.db

import androidx.room.*
import com.example.speedreader.data.model.PdfBook

@Dao
interface PdfBookDao {
    @Query("SELECT * FROM pdf_books")
    suspend fun getAll(): List<PdfBook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(book: PdfBook)

    @Query("DELETE FROM pdf_books WHERE uri = :uri")
    suspend fun delete(uri: String)
}