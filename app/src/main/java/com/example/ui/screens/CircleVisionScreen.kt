package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DocAnalysisUiState
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.viewmodel.AuraViewModel
import com.example.ui.viewmodel.VisionUiState
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun CircleVisionScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val visionState by viewModel.visionState.collectAsStateWithLifecycle()
    val docState by viewModel.docAnalysisState.collectAsStateWithLifecycle()

    var modeTab by remember { mutableStateOf(0) } // 0 = Circle Vision, 1 = PDF & File AI
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokePoints = remember { mutableStateListOf<Offset>() }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var pickedMimeType by remember { mutableStateOf<String?>(null) }
    var pickedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var filePickError by remember { mutableStateOf<String?>(null) }
    var docQuestion by remember { mutableStateOf("") }

    // Zero-permission Storage Access Framework picker for PDFs, text, and images
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            filePickError = null
            val mime = context.contentResolver.getType(uri) ?: "application/pdf"
            val name = resolveDisplayName(context, uri) ?: "document"
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    filePickError = "Could not read the selected file."
                } else if (bytes.size > 15 * 1024 * 1024) {
                    filePickError = "File exceeds the 15 MB limit for multimodal analysis."
                } else {
                    pickedFileName = name
                    pickedMimeType = mime
                    pickedFileBytes = bytes
                }
            } catch (e: Exception) {
                filePickError = e.localizedMessage ?: "Failed to open selected file."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (activeBitmap == null) {
            activeBitmap = loadScaledSampleBitmap(context, R.drawable.img_circle_sample_scene_1795359592715)
        }
    }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadScaledUriBitmap(context, uri)?.let { bmp ->
                activeBitmap = bmp
                strokePoints.clear()
                selectionRect = null
                viewModel.resetVisionResult()
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            loadScaledUriBitmap(context, uri)?.let { bmp ->
                activeBitmap = bmp
                strokePoints.clear()
                selectionRect = null
                viewModel.resetVisionResult()
            }
        }
    }

    fun launchCameraCapture() {
        val dir = File(context.cacheDir, "vision").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTempUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraCapture()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (modeTab == 0) "Circle to Identify" else "PDF & File AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (modeTab == 0) {
                        "Draw a circle around any object for real-time Gemini Vision"
                    } else {
                        "Analyze PDFs, notes, contracts, or screenshots with Gemini 3.8 Flash"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (modeTab == 0 && selectionRect != null) {
                IconButton(
                    onClick = {
                        strokePoints.clear()
                        selectionRect = null
                        viewModel.resetVisionResult()
                    },
                    modifier = Modifier.testTag("clear_circle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.cd_clear_circle)
                    )
                }
            }
        }

        // Segmented Mode Toggle: Circle Vision | PDF & File AI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple(0, "Circle Vision", Icons.Rounded.Gesture),
                Triple(1, "PDF & File AI", Icons.Rounded.Description)
            ).forEach { (idx, label, icon) ->
                val selected = modeTab == idx
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { modeTab = idx }
                        .testTag("vision_mode_tab_$idx")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (modeTab == 1) {
            DocumentFileAnalyzerSection(
                pickedFileName = pickedFileName,
                pickedFileSizeKb = pickedFileBytes?.size?.div(1024),
                filePickError = filePickError,
                question = docQuestion,
                onQuestionChange = { docQuestion = it },
                docState = docState,
                onPickFile = {
                    docPickerLauncher.launch(arrayOf("application/pdf", "text/*", "image/*"))
                },
                onAnalyze = {
                    val bytes = pickedFileBytes ?: return@DocumentFileAnalyzerSection
                    val mime = pickedMimeType ?: "application/pdf"
                    val name = pickedFileName ?: "document"
                    viewModel.analyzeDocument(bytes, mime, name, docQuestion)
                },
                onSpeakResult = { text -> viewModel.voiceEngine.speak(text) }
            )
            return@Column
        }

        // Source Picker Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("pick_photo_button")
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Gallery", style = MaterialTheme.typography.labelLarge)
            }

            FilledTonalButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCameraCapture()
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("take_photo_button")
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Camera", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = {
                    activeBitmap = loadScaledSampleBitmap(
                        context,
                        R.drawable.img_circle_sample_scene_1795359592715
                    )
                    strokePoints.clear()
                    selectionRect = null
                    viewModel.resetVisionResult()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("load_sample_scene_button")
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.cd_sample_scene))
            }
        }

        // Interactive Circle-to-Identify Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(activeBitmap, canvasSize) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            strokePoints.clear()
                            strokePoints.add(startOffset)
                            selectionRect = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val clamped = Offset(
                                change.position.x.coerceIn(0f, size.width.toFloat()),
                                change.position.y.coerceIn(0f, size.height.toFloat())
                            )
                            strokePoints.add(clamped)
                        },
                        onDragEnd = {
                            val bmp = activeBitmap
                            if (bmp != null && strokePoints.size >= 4 && canvasSize.width > 0 && canvasSize.height > 0) {
                                val minX = strokePoints.minOf { it.x }
                                val maxX = strokePoints.maxOf { it.x }
                                val minY = strokePoints.minOf { it.y }
                                val maxY = strokePoints.maxOf { it.y }
                                if ((maxX - minX) > 28f && (maxY - minY) > 28f) {
                                    val rect = Rect(minX, minY, maxX, maxY)
                                    selectionRect = rect
                                    val cropped = cropDisplayedRegion(bmp, rect, canvasSize)
                                    val contextThumb = scaleDownBitmap(bmp, 640)
                                    viewModel.analyzeCircledRegion(cropped, contextThumb)
                                }
                            }
                        }
                    )
                }
                .testTag("circle_vision_canvas"),
            contentAlignment = Alignment.Center
        ) {
            val bmp = activeBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_visual_canvas),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Glowing Freehand Lasso & Bounding Highlight Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (strokePoints.size > 1) {
                    val path = Path().apply {
                        moveTo(strokePoints.first().x, strokePoints.first().y)
                        for (i in 1 until strokePoints.size) {
                            lineTo(strokePoints[i].x, strokePoints[i].y)
                        }
                    }
                    // Soft glow aura
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(listOf(AuraCyan, AuraCobalt, AuraMint)),
                        style = Stroke(
                            width = 14.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        ),
                        alpha = 0.30f
                    )
                    // Sharp luminous core
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(listOf(AuraMint, AuraCyan, AuraCobalt)),
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                selectionRect?.let { rect ->
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(AuraCyan, AuraMint)),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            if (strokePoints.isEmpty() && selectionRect == null) {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Circle any object with your finger to identify it live",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Real-Time Gemini Visual Identification Result Card
        when (val state = visionState) {
            is VisionUiState.Ready -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Try circling the sage coffee mug, mechanical keyboard, watch, earbuds, or plant above — or pick your own screenshot!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is VisionUiState.Analyzing -> {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Identifying circled region...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Optimized multimodal crop → Gemini 3.8 Flash",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            is VisionUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            is VisionUiState.Identified -> {
                val res = state.result
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .testTag("vision_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = res.objectName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.voiceEngine.speak("${res.objectName}. ${res.summary}") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = stringResource(R.string.cd_speak_text),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = res.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (res.keyDetails.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                res.keyDetails.forEach { detail ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "• $detail",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.prefillWhatsAppFromVision(res) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("vision_to_whatsapp_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Draft WhatsApp Message About This")
                        }
                    }
                }
            }
        }
    }
}

private fun loadScaledSampleBitmap(context: Context, resId: Int): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, opts)
        val sample = computeInSampleSize(opts.outWidth, opts.outHeight, 1280)
        BitmapFactory.decodeResource(
            context.resources,
            resId,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (_: Exception) {
        null
    }
}

private fun loadScaledUriBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val sample = computeInSampleSize(opts.outWidth, opts.outHeight, 1280)
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    }
}

private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= maxDim || h / 2 >= maxDim) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample
}

private fun scaleDownBitmap(src: Bitmap, maxDim: Int): Bitmap {
    val largest = max(src.width, src.height)
    if (largest <= maxDim) return src
    val scale = maxDim.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

/**
 * Maps a ContentScale.Fit display rectangle accurately back to source bitmap pixels
 * and returns a clean, memory-safe cropped Bitmap for Gemini Vision.
 */
private fun cropDisplayedRegion(
    bitmap: Bitmap,
    selection: Rect,
    containerSize: IntSize
): Bitmap {
    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()
    val boxW = containerSize.width.toFloat()
    val boxH = containerSize.height.toFloat()
    if (bmpW <= 0f || bmpH <= 0f || boxW <= 0f || boxH <= 0f) return scaleDownBitmap(bitmap, 768)

    val scale = min(boxW / bmpW, boxH / bmpH)
    val drawnW = bmpW * scale
    val drawnH = bmpH * scale
    val offsetX = (boxW - drawnW) / 2f
    val offsetY = (boxH - drawnH) / 2f

    val left = ((selection.left - offsetX) / scale).toInt().coerceIn(0, bitmap.width - 1)
    val top = ((selection.top - offsetY) / scale).toInt().coerceIn(0, bitmap.height - 1)
    val right = ((selection.right - offsetX) / scale).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ((selection.bottom - offsetY) / scale).toInt().coerceIn(top + 1, bitmap.height)

    val padX = ((right - left) * 0.10f).toInt()
    val padY = ((bottom - top) * 0.10f).toInt()
    val x0 = (left - padX).coerceAtLeast(0)
    val y0 = (top - padY).coerceAtLeast(0)
    val x1 = (right + padX).coerceAtMost(bitmap.width)
    val y1 = (bottom + padY).coerceAtMost(bitmap.height)

    val cropW = (x1 - x0).coerceAtLeast(16).coerceAtMost(bitmap.width - x0)
    val cropH = (y1 - y0).coerceAtLeast(16).coerceAtMost(bitmap.height - y0)

    val cropped = Bitmap.createBitmap(bitmap, x0, y0, cropW, cropH)
    return scaleDownBitmap(cropped, 768)
}

@Composable
private fun DocumentFileAnalyzerSection(
    pickedFileName: String?,
    pickedFileSizeKb: Int?,
    filePickError: String?,
    question: String,
    onQuestionChange: (String) -> Unit,
    docState: DocAnalysisUiState,
    onPickFile: () -> Unit,
    onAnalyze: () -> Unit,
    onSpeakResult: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onPickFile,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("pick_document_button")
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (pickedFileName == null) "Choose PDF, Text, or Image File" else "Change File ($pickedFileName)",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (pickedFileName != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pickedFileName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${pickedFileSizeKb ?: 1} KB • Ready for Gemini 3.8 Flash",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!filePickError.isNullOrBlank()) {
                Text(
                    text = filePickError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                label = { Text("Question or task (optional)") },
                placeholder = { Text("e.g., Summarize key takeaways, dates, and action items") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("doc_question_input")
            )

            Button(
                onClick = onAnalyze,
                enabled = pickedFileName != null && docState !is DocAnalysisUiState.Loading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("analyze_document_button")
            ) {
                if (docState is DocAnalysisUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Analyzing with Gemini...")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Analyze Document")
                }
            }
        }
    }

    when (docState) {
        is DocAnalysisUiState.Idle -> Unit
        is DocAnalysisUiState.Loading -> Unit
        is DocAnalysisUiState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = docState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        is DocAnalysisUiState.Success -> {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .testTag("doc_analysis_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MULTIMODAL ANALYSIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = docState.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { onSpeakResult(docState.analysis) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.cd_speak_text),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = docState.analysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
