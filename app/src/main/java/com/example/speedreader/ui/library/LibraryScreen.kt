package com.example.speedreader.ui.library

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import java.io.File

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import com.example.speedreader.data.db.PdfBookDao
import com.example.speedreader.data.db.UserStatsDao
import com.example.speedreader.data.model.UserStats
import kotlinx.coroutines.launch

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    pdfBookDao: PdfBookDao,
    onPdfSelected: (Uri, String) -> Unit,
    userStatsDao: UserStatsDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfList by remember { mutableStateOf(listOf<com.example.speedreader.data.model.PdfBook>()) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var pdfToRename by remember { mutableStateOf<com.example.speedreader.data.model.PdfBook?>(null) }
    var newName by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var stats by remember { mutableStateOf<UserStats?>(null) }

    LaunchedEffect(userStatsDao) {
        val today = java.time.LocalDate.now()
        val s = userStatsDao.getStats() ?: UserStats()

        val lastRead = if (s.lastReadDate.isNotBlank()) java.time.LocalDate.parse(s.lastReadDate) else null

        val updatedStats = if (lastRead == null || lastRead.isBefore(today)) {
            // Determine yesterday's words
            val yesterdayWords = if (lastRead == today.minusDays(1)) s.todayWords else 0

            // Reset streak if yesterday < 3000 or last reading >1 day ago
            val newStreak = if (yesterdayWords >= 3000) s.streak else 0

            s.copy(
                streak = newStreak,
                todayWords = 0,
                yesterdayWords = yesterdayWords,
                lastReadDate = today.toString(),
                streakUpdatedDate = "" // allow streak increment today
            )
        } else {
            s
        }

        userStatsDao.insertOrUpdate(updatedStats)
        stats = updatedStats

        android.util.Log.d("SpeedReaderStats", "LibraryScreen new day check: $updatedStats")
    }

    // PDF picker
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val contentResolver = context.contentResolver

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                scope.launch {
                    val destFile = File(context.getExternalFilesDir(null), uri.lastPathSegment ?: "document.pdf")

                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val savedBook = com.example.speedreader.data.model.PdfBook(
                        uri = destFile.toUri().toString(),
                        name = uri.lastPathSegment ?: "Unknown.pdf"
                    )
                    pdfBookDao.insertOrUpdate(savedBook)
                    pdfList = pdfBookDao.getAll()
                }
            }
        }
    )

    // Load PDFs from DB
    LaunchedEffect(Unit) {
        pdfList = pdfBookDao.getAll()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Library") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                Text("+")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {

            // --- Rename & Delete Dialog ---
            if (showRenameDialog && pdfToRename != null) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename PDF") },
                    text = {
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("New name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val updatedBook = pdfToRename!!.copy(name = newName)
                            scope.launch {
                                pdfBookDao.insertOrUpdate(updatedBook)
                                pdfList = pdfBookDao.getAll()
                            }
                            showRenameDialog = false
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Row {
                            Button(
                                onClick = {
                                    showDeleteConfirmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                            ) {
                                Text("Delete")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { showRenameDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }

            // --- Delete Confirmation Dialog ---
            if (showDeleteConfirmDialog && pdfToRename != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Confirm Deletion") },
                    text = { Text("Are you sure you want to delete '${pdfToRename!!.name}'?") },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                pdfBookDao.delete(pdfToRename!!.uri)
                                pdfList = pdfBookDao.getAll()
                            }
                            showDeleteConfirmDialog = false
                            showRenameDialog = false
                        }) {
                            Text("Delete", color = androidx.compose.ui.graphics.Color.White)
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Streaks section
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Total words read: ${stats?.totalWordsRead ?: 0}",
                    fontSize = 20.sp
                )
                Text(
                    text = "Today words read: ${stats?.todayWords ?: 0}",
                    fontSize = 20.sp
                )
                Text(
                    text = "Streak: ${stats?.streak ?: 0} days",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- PDF List ---
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pdfList) { pdf ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .combinedClickable(
                                onClick = { onPdfSelected(Uri.parse(pdf.uri), pdf.name) },
                                onLongClick = {
                                    pdfToRename = pdf
                                    newName = pdf.name
                                    showRenameDialog = true
                                }
                            )
                    ) {
                        Text(text = pdf.name)
                    }
                }
            }
        }
    }
}

// --- Helper to load a default PDF ---
fun getDefaultPdfUri(context: android.content.Context): Uri {
    val file = File(context.cacheDir, "default.pdf")
    if (!file.exists()) {
        context.assets.open("default.pdf").use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return file.toUri()
}
