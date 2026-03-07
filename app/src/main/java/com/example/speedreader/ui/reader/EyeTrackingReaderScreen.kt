package com.example.speedreader.ui.reader

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeTrackingReaderScreen(
    pdfUri: Uri,
    pdfName: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // State
    var words by remember { mutableStateOf(listOf<String>()) }
    var currentPage by remember { mutableIntStateOf(0) }
    val wordsPerPage = 30 // Configurable sentence/word limit

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isLookingAtScreen by remember { mutableStateOf(false) }
    var activeReadingTimeSeconds by remember { mutableFloatStateOf(0f) }
    var showStats by remember { mutableStateOf(false) }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        // Load words here (reusing your extractTextFromPdfCached function)
        val extractedWords = extractTextFromPdfCached(context, pdfUri)
        words = extractedWords.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // Timer logic: Only increment if the user is looking at the screen
    LaunchedEffect(isLookingAtScreen, showStats) {
        while (isLookingAtScreen && !showStats) {
            delay(100) // update every 100ms
            activeReadingTimeSeconds += 0.1f
        }
    }

    if (showStats) {
        // --- STATS SCREEN ---
        val wordsRead = (currentPage + 1) * wordsPerPage
        val wpm = if (activeReadingTimeSeconds > 0) {
            (wordsRead / (activeReadingTimeSeconds / 60)).toInt()
        } else 0

        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Reading Session Complete!") },
            text = {
                Column {
                    Text("Total Words Read: $wordsRead")
                    Text("Time Focused: ${String.format("%.1f", activeReadingTimeSeconds)} seconds")
                    Text("Your Speed: $wpm WPM", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { navController.popBackStack() }) { Text("Awesome") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Eye Tracker: $pdfName") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Indicator
            Text(
                text = if (isLookingAtScreen) "Tracking: Eyes Detected" else "Tracking Paused: Please look at the screen",
                color = if (isLookingAtScreen) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Book Text
            val pages = words.chunked(wordsPerPage)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (pages.isNotEmpty() && currentPage < pages.size) {
                    Text(
                        text = pages[currentPage].joinToString(" "),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                } else {
                    Text("Loading or end of book...")
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { if (currentPage > 0) currentPage-- }) { Text("<") }

                Button(
                    onClick = { showStats = true },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                ) {
                    Text("Stop & Show Stats")
                }

                Button(onClick = { if (currentPage < pages.size - 1) currentPage++ }) { Text(">") }
            }

            // Hidden Camera Preview for MediaPipe Analyzer
            if (hasCameraPermission && !showStats) {
                Box(modifier = Modifier.size(1.dp)) { // Keep it tiny/hidden
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val executor = ContextCompat.getMainExecutor(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                            // TODO: Pass frame to MediaPipe
                                            // Mock logic: Assuming face is detected if camera is running
                                            // Real implementation requires FaceLandmarker
                                            isLookingAtScreen = true
                                            imageProxy.close()
                                        }
                                    }

                                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner, cameraSelector, preview, imageAnalyzer
                                    )
                                } catch (exc: Exception) {
                                    Log.e("EyeTracker", "Use case binding failed", exc)
                                }
                            }, executor)
                            previewView
                        }
                    )
                }
            }
        }
    }
}