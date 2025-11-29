package com.example.speedreader.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedreader.data.db.PdfBookDao
import com.example.speedreader.data.db.UserStatsDao
import com.example.speedreader.data.model.PdfBook
import com.example.speedreader.data.model.UserStats
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedReaderScreen(
    pdfUri: Uri,
    pdfName: String,
    pdfBookDao: PdfBookDao,
    userStatsDao: UserStatsDao
) {
    val context = LocalContext.current
    var words by remember { mutableStateOf(listOf<String>()) }
    var currentWordIndex by remember { mutableIntStateOf(0) }
    var wpm by remember { mutableIntStateOf(300) }
    var isPaused by remember { mutableStateOf(true) }
    var showFullText by remember { mutableStateOf(false) }
    var showPercentage by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = LocalDate.now().format(dateFormatter)

    var stats by remember { mutableStateOf<UserStats?>(null) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { userStatsDao.getStats() }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(currentWordIndex, showFullText) {
        if (showFullText && words.isNotEmpty()) {
            listState.animateScrollToItem(currentWordIndex)
        }
    }

    // Load PDF text
    LaunchedEffect(pdfUri) {
        val extractedWords = extractTextFromPdfCached(context, pdfUri)
        words = extractedWords.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val savedBook = withContext(Dispatchers.IO) {
            pdfBookDao.getAll().find { it.uri == pdfUri.toString() }
        }
        currentWordIndex = savedBook?.lastWordIndex ?: 0
    }

    // Flash words
    LaunchedEffect(words, wpm, isPaused) {
        while (words.isNotEmpty()) {
            if (!isPaused) {
                currentWordIndex = (currentWordIndex + 1).coerceAtMost(words.size - 1)
                pdfBookDao.insertOrUpdate(
                    PdfBook(uri = pdfUri.toString(), name = pdfName, lastWordIndex = currentWordIndex)
                )

                // --- STREAK + WORD COUNT TRACKING ---
                withContext(Dispatchers.IO) {
                    val today = java.time.LocalDate.now().toString()
                    val s = stats ?: userStatsDao.getStats() ?: UserStats()

                    // Increment todayWords and totalWordsRead
                    val isNewDay = s.lastReadDate != today
                    val newTodayCount = if (isNewDay) 1 else s.todayWords + 1
                    val newTotal = s.totalWordsRead + 1

                    // Only increment streak once per day when reaching 3000 words
                    val reached3000Today = newTodayCount >= 3000 && s.streakUpdatedDate != today
                    val updatedStreak = if (reached3000Today) s.streak + 1 else s.streak

                    val updatedStats = s.copy(
                        totalWordsRead = newTotal,
                        todayWords = newTodayCount,
                        lastReadDate = today,
                        streak = updatedStreak,
                        streakUpdatedDate = if (reached3000Today) today else s.streakUpdatedDate
                    )

                    // Insert or update in DB
                    userStatsDao.insertOrUpdate(updatedStats)
                    stats = updatedStats

                    android.util.Log.d("SpeedReaderStats", "Updated stats: $updatedStats")
                }


            }
            val delayMillis = (60000L / wpm)
            delay(delayMillis)
        }
    }

    val remainingWords = (words.size - currentWordIndex).coerceAtLeast(0)
    val timeLeftMinutes = if (wpm > 0) remainingWords.toDouble() / wpm else 0.0
    val totalMinutes = timeLeftMinutes.toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timeFormatted = String.format("%dh %02dm", hours, minutes)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Speed Reader") }) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { showFullText = !showFullText }) {
                        Text(if (showFullText) "Hide Full Text" else "Show Full Text")
                    }

                    // <-- Word counter / percentage toggle
                    Text(
                        text = if (showPercentage && words.isNotEmpty())
                            "${(currentWordIndex * 100 / words.size)}%"
                        else
                            "${currentWordIndex + 1}/${words.size}",
                        modifier = Modifier
                            .clickable { showPercentage = !showPercentage }
                            .padding(8.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (words.isEmpty()) {
                    Text("Loading PDF...", fontSize = 24.sp)
                } else {
                    Text(text = words[currentWordIndex], fontSize = 48.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Decrease by 50
                        Button(onClick = { if (wpm > 50) wpm -= 50 }) { Text("--") }

                        // Decrease by 10
                        Button(onClick = { if (wpm > 10) wpm -= 10 }) { Text("-") }

                        Text("WPM: $wpm", fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterVertically))

                        // Increase by 10
                        Button(onClick = { wpm += 10 }) { Text("+") }

                        // Increase by 50
                        Button(onClick = { wpm += 50 }) { Text("++") }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { isPaused = !isPaused }) {
                        Text(if (isPaused) "Play" else "Pause")
                    }

                    if (showFullText) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp)
                        ) {
                            itemsIndexed(words) { index, word ->
                                Text(
                                    text = word,
                                    fontSize = 16.sp,
                                    color = if (index == currentWordIndex)
                                        androidx.compose.ui.graphics.Color.Red
                                    else
                                        androidx.compose.ui.graphics.Color.Black,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(2.dp)
                                        .clickable { currentWordIndex = index }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = timeFormatted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                fontSize = 14.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}

// --- Helper function to extract text from a PDF ---
fun extractTextFromPdf(context: Context, pdfUri: Uri): String {
    var text = ""
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(pdfUri)
        inputStream?.use { stream ->
            val document = PDDocument.load(stream)
            val stripper = PDFTextStripper()
            text = stripper.getText(document)
            document.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return text
}

suspend fun extractTextFromPdfCached(context: Context, pdfUri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val cacheDir = context.getExternalFilesDir("pdf_cache")
        if (cacheDir?.exists() == false) cacheDir.mkdirs()

        val cacheFile = File(cacheDir, "${pdfUri.hashCode()}.txt")

        if (cacheFile.exists()) {
            return@withContext cacheFile.readText()
        }

        val inputStream: InputStream? = context.contentResolver.openInputStream(pdfUri)
        val text = buildString {
            inputStream?.use { stream ->
                PDDocument.load(stream).use { document ->
                    val stripper = PDFTextStripper()
                    append(stripper.getText(document))
                }
            }
        }

        cacheFile.writeText(text)

        return@withContext text
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}