package com.example.speedreader.ui.reader

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.sqrt

// A thread-safe bridge between Compose and MediaPipe
class TrackerState {
    @Volatile var tlIrisX = 0f
    @Volatile var tlIrisY = 0f
    @Volatile var brIrisX = 0f
    @Volatile var brIrisY = 0f
    @Volatile var isCalibrating = true
    @Volatile var currentRawIrisX = 0f
    @Volatile var currentRawIrisY = 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeTrackingReaderScreen(
    pdfUri: Uri,
    pdfName: String,
    type: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val trackerState = remember { TrackerState() }

    var words by remember { mutableStateOf(listOf<String>()) }
    var currentPage by remember { mutableIntStateOf(0) }
    val wordsPerPage = 30

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isLookingAtScreen by remember { mutableStateOf(false) }
    var activeReadingTimeSeconds by remember { mutableFloatStateOf(0f) }
    var showStats by remember { mutableStateOf(false) }

    var dotXNorm by remember { mutableFloatStateOf(0.5f) }
    var dotYNorm by remember { mutableFloatStateOf(0.5f) }

    var isCalibratingUI by remember { mutableStateOf(true) }
    var calibrationStep by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        // Fetch text dynamically based on type
        val extractedText = if (type == "web") {
            extractTextFromWeb(pdfUri.toString())
        } else {
            extractTextFromPdfCached(context, pdfUri)
        }
        words = extractedText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        //words = List(300) { "Word$it" }
    }

    LaunchedEffect(isLookingAtScreen, showStats) {
        while (isLookingAtScreen && !showStats) {
            delay(100)
            activeReadingTimeSeconds += 0.1f
        }
    }

    if (showStats) {
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
                Button(onClick = { navController.popBackStack() }) { Text("Okay") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Eye Tracker: $pdfName") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isCalibratingUI) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Keep your head perfectly still.\nLook at the button and tap it.",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )

                    if (calibrationStep == 0) {
                        Button(
                            onClick = {
                                trackerState.tlIrisX = trackerState.currentRawIrisX
                                trackerState.tlIrisY = trackerState.currentRawIrisY
                                calibrationStep = 1
                            },
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        ) { Text("Look Here & Tap") }
                    } else if (calibrationStep == 1) {
                        Button(
                            onClick = {
                                trackerState.brIrisX = trackerState.currentRawIrisX
                                trackerState.brIrisY = trackerState.currentRawIrisY
                                trackerState.isCalibrating = false
                                isCalibratingUI = false
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) { Text("Look Here & Tap") }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isLookingAtScreen) "Tracking: Eyes Detected" else "Tracking Paused: Please look at the screen",
                            color = if (isLookingAtScreen) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = { if (currentPage > 0) currentPage-- }) { Text("<") }
                            Button(
                                onClick = { showStats = true },
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                            ) { Text("Stop & Show Stats") }
                            Button(onClick = { if (currentPage < pages.size - 1) currentPage++ }) { Text(">") }
                        }
                    }

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f),
                            radius = 20f,
                            center = androidx.compose.ui.geometry.Offset(
                                x = dotXNorm * size.width,
                                y = dotYNorm * size.height
                            )
                        )
                    }
                }
            }

            if (hasCameraPermission && !showStats) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd) // Puts it in the corner
                        .padding(16.dp)
                        .size(100.dp, 140.dp) // Width and Height of your "mirror"
                        .clip(RoundedCornerShape(12.dp)) // Gives it nice rounded corners
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val executor = ContextCompat.getMainExecutor(ctx)
                            val backgroundExecutor = Executors.newSingleThreadExecutor()
                            var lastEyesOpenTime = SystemClock.elapsedRealtime()

                            val modelBuffer = loadModelFile(context, "face_landmarker.task")
                            val baseOptions = BaseOptions.builder()
                                .setModelAssetBuffer(modelBuffer)
                                .build()

                            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                                .setBaseOptions(baseOptions)
                                .setRunningMode(RunningMode.LIVE_STREAM)
                                .setResultListener { result, _ ->
                                    val faceLandmarks = result.faceLandmarks()
                                    val currentTime = SystemClock.elapsedRealtime()

                                    if (faceLandmarks.isNotEmpty()) {
                                        val landmarks = faceLandmarks[0]

                                        val rightEyeIndices = intArrayOf(33, 160, 158, 133, 153, 144)
                                        val leftEyeIndices = intArrayOf(362, 385, 387, 263, 373, 380)
                                        val avgEAR = (calculateEAR(landmarks, rightEyeIndices) + calculateEAR(landmarks, leftEyeIndices)) / 2.0f

                                        if (avgEAR > 0.2f) {
                                            lastEyesOpenTime = currentTime
                                            isLookingAtScreen = true
                                        } else if (currentTime - lastEyesOpenTime > 500L) {
                                            isLookingAtScreen = false
                                        }

                                        // --- NEW LOGIC: Relative Eye Coordinates ---
                                        // We use the right eye (landmarks 33, 133, 159, 145) and right iris (468)
                                        // --- STABILIZED LOGIC: Rigid Relative Coordinates ---
// We use the inner (133) and outer (33) corners of the right eye.
// These points do NOT move when you blink, providing a rock-solid anchor.
                                        val innerCorner = landmarks[133]
                                        val outerCorner = landmarks[33]
                                        val rawIris = landmarks[468]

// Calculate the distance between corners to use as our stable "scale"
                                        val dxCorner = innerCorner.x() - outerCorner.x()
                                        val dyCorner = innerCorner.y() - outerCorner.y()
                                        val eyeWidth = sqrt(dxCorner * dxCorner + dyCorner * dyCorner)
                                        val safeEyeWidth = maxOf(eyeWidth, 0.0001f)

// Calculate iris offset from the inner corner, scaled strictly by the static eye width
                                        val relativeIrisX = (rawIris.x() - innerCorner.x()) / safeEyeWidth
                                        val relativeIrisY = (rawIris.y() - innerCorner.y()) / safeEyeWidth

                                        trackerState.currentRawIrisX = relativeIrisX
                                        trackerState.currentRawIrisY = relativeIrisY

                                        if (!trackerState.isCalibrating && isLookingAtScreen) {
                                            val rangeX = trackerState.brIrisX - trackerState.tlIrisX
                                            val rangeY = trackerState.brIrisY - trackerState.tlIrisY

                                            // Increased the minimum range threshold to 0.002f to prevent extreme division spikes
                                            // if calibration points were captured too closely together.
                                            val minRange = 0.002f
                                            val safeRangeX = if (kotlin.math.abs(rangeX) < minRange) if (rangeX >= 0) minRange else -minRange else rangeX
                                            val safeRangeY = if (kotlin.math.abs(rangeY) < minRange) if (rangeY >= 0) minRange else -minRange else rangeY

                                            var targetX = (relativeIrisX - trackerState.tlIrisX) / safeRangeX
                                            var targetY = (relativeIrisY - trackerState.tlIrisY) / safeRangeY

                                            targetX = targetX.coerceIn(0f, 1f)
                                            targetY = targetY.coerceIn(0f, 1f)

                                            ContextCompat.getMainExecutor(ctx).execute {
                                                // Reduced smoothing factor slightly to make it feel less "floaty"
                                                val smoothingFactor = 0.1f
                                                dotXNorm = (dotXNorm * (1f - smoothingFactor)) + (targetX * smoothingFactor)
                                                dotYNorm = (dotYNorm * (1f - smoothingFactor)) + (targetY * smoothingFactor)
                                            }
                                        }
                                    } else {
                                        if (currentTime - lastEyesOpenTime > 500L) {
                                            isLookingAtScreen = false
                                        }
                                    }
                                }
                                .setErrorListener { error -> Log.e("EyeTracker", "MediaPipe Error: ${error.message}") }
                                .build()

                            val faceLandmarker = FaceLandmarker.createFromOptions(ctx, options)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analyzer ->
                                        analyzer.setAnalyzer(backgroundExecutor) { imageProxy ->
                                            val frameTime = SystemClock.uptimeMillis()
                                            try {
                                                val bitmap = imageProxy.toBitmap()
                                                val matrix = Matrix().apply {
                                                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                                    postScale(-1f, 1f)
                                                }
                                                val mirroredBitmap = Bitmap.createBitmap(
                                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                                )
                                                val mpImage = BitmapImageBuilder(mirroredBitmap).build()
                                                faceLandmarker.detectAsync(mpImage, frameTime)
                                            } catch (e: Exception) {
                                                Log.e("EyeTracker", "Image processing failed", e)
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer
                                    )
                                } catch (exc: Exception) {
                                    Log.e("EyeTracker", "Use case binding failed", exc)
                                }
                            }, executor)
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
    val afd = context.assets.openFd(modelName)
    FileInputStream(afd.fileDescriptor).use { fis ->
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}

fun euclideanDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
    val dx = p1.x() - p2.x()
    val dy = p1.y() - p2.y()
    return sqrt(dx * dx + dy * dy)
}

fun calculateEAR(landmarks: List<NormalizedLandmark>, indices: IntArray): Float {
    val p1 = landmarks[indices[0]]
    val p2 = landmarks[indices[1]]
    val p3 = landmarks[indices[2]]
    val p4 = landmarks[indices[3]]
    val p5 = landmarks[indices[4]]
    val p6 = landmarks[indices[5]]
    val vertical1 = euclideanDistance(p2, p6)
    val vertical2 = euclideanDistance(p3, p5)
    val horizontal = euclideanDistance(p1, p4)
    return (vertical1 + vertical2) / (2.0f * horizontal)
}