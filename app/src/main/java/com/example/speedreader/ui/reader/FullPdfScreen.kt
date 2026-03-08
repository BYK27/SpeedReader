package com.example.speedreader.ui.reader

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.speedreader.data.db.PdfBookDao
import com.example.speedreader.data.model.PdfBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPdfScreen(
    pdfUri: Uri,
    pdfName: String,
    type: String,
    pdfBookDao: PdfBookDao,
    navController: NavHostController
) {
    val context = LocalContext.current
    var words by remember { mutableStateOf(listOf<String>()) }
    var isLoaded by remember { mutableStateOf(false) }

    // Text size and formatting states
    var textSize by remember { mutableFloatStateOf(18f) }
    val wordsPerPage = 250 // Adjust this number for standard density
    var initialPage by remember { mutableIntStateOf(0) }

    // Load PDF text and progress
    LaunchedEffect(pdfUri) {
        if (type == "web") {
            val extractedWords = extractTextFromWeb(pdfUri.toString())
            words = extractedWords.split("\\s+".toRegex()).filter { it.isNotBlank() }
            initialPage = 0
            isLoaded = true
        } else {
            val extractedWords = extractTextFromPdfCached(context, pdfUri)
            words = extractedWords.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val savedBook = withContext(Dispatchers.IO) {
                pdfBookDao.getAll().find { it.uri == pdfUri.toString() }
            }
            val lastWordIndex = savedBook?.lastWordIndex ?: 0
            initialPage = if (words.isNotEmpty()) lastWordIndex / wordsPerPage else 0
            isLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pdfName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // This is the < icon
                            contentDescription = "Go Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { if (textSize > 12f) textSize -= 2f }) {
                        Text("A-", fontSize = 16.sp)
                    }
                    IconButton(onClick = { if (textSize < 36f) textSize += 2f }) {
                        Text("A+", fontSize = 16.sp)
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading book...")
            }
        } else if (words.isNotEmpty()) {
            val pages = words.chunked(wordsPerPage)
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { pages.size }
            )

            // Save progress to database whenever the user flips a page
            LaunchedEffect(pagerState.currentPage) {
                if (type == "pdf") {
                    val newIndex = pagerState.currentPage * wordsPerPage
                    withContext(Dispatchers.IO) {
                        pdfBookDao.insertOrUpdate(
                            PdfBook(uri = pdfUri.toString(), name = pdfName, lastWordIndex = newIndex)
                        )
                    }
                }
            }

            // Book layout
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { page ->
                val pageText = pages[page].joinToString(" ")

                // Allow vertical scrolling in case the text size is set very large and overflows
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = pageText,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.5).sp, // Improve readability
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )

                    // Page indicator
                    Text(
                        text = "${page + 1} / ${pages.size}",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}