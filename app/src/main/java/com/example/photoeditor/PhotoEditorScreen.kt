package com.example.photoeditor

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ai.core.*

import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    uriString: String,
    onBack: () -> Unit,
    onExported: (String) -> Unit
) {
    val viewModel: PhotoEditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val originalBitmapState by viewModel.originalBitmap.collectAsState()
    val previewBitmapState by viewModel.previewBitmap.collectAsState()
    val originalBitmap = previewBitmapState ?: originalBitmapState
    
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentTab by remember { mutableStateOf("ADJUST") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(uriString) {
        viewModel.setUri(uriString)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo))
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo))
                    }
                    IconButton(onClick = {
                        if (isSaving) return@IconButton
                        isSaving = true
                        coroutineScope.launch {
                            val outUri = PhotoExport.export(context, state)
                            isSaving = false
                            if (outUri != null) {
                                Toast.makeText(context, context.getString(R.string.editor_save_success), Toast.LENGTH_SHORT).show()
                                onExported(outUri.toString())
                            } else {
                                Toast.makeText(context, context.getString(R.string.editor_save_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.editor_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            val originalW = originalBitmap?.width?.toFloat() ?: 1f
            val originalH = originalBitmap?.height?.toFloat() ?: 1f
            val angle = state.rotation + state.straighten
            val rad = Math.toRadians(angle.toDouble())
            val cos = Math.abs(Math.cos(rad)).toFloat()
            val sin = Math.abs(Math.sin(rad)).toFloat()
            val fullW = originalW * cos + originalH * sin
            val fullH = originalW * sin + originalH * cos
            EditorBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it }, viewModel = viewModel, state = state, fullW = fullW, fullH = fullH)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (originalBitmap != null) {
                BoxWithConstraints {
                    val boxWidth = constraints.maxWidth.toFloat()
                    val boxHeight = constraints.maxHeight.toFloat()
                    
                    val originalW = originalBitmap!!.width.toFloat()
                    val originalH = originalBitmap!!.height.toFloat()
                    val angle = state.rotation + state.straighten
                    val rad = Math.toRadians(angle.toDouble())
                    val cos = Math.abs(Math.cos(rad)).toFloat()
                    val sin = Math.abs(Math.sin(rad)).toFloat()
                    val fullW = originalW * cos + originalH * sin
                    val fullH = originalW * sin + originalH * cos
                    
                    val isCropMode = currentTab == "CROP"
                    val displayW = if (isCropMode) fullW else fullW * state.cropRect.width
                    val displayH = if (isCropMode) fullH else fullH * state.cropRect.height
                    
                    val scale = minOf(boxWidth / displayW, boxHeight / displayH)
                    val renderWidth = displayW * scale
                    val renderHeight = displayH * scale
                    
                    Box(
                        modifier = Modifier
                            .size((renderWidth / density.density).dp, (renderHeight / density.density).dp)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(currentTab, state.cropAspectRatio, fullW, fullH, scale) {
                            if (isCropMode) {
                                var activeCropHandle: CropHandle? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val imageW = fullW * scale
                                        val imageH = fullH * scale
                                        val cx = state.cropRect.left * imageW
                                        val cy = state.cropRect.top * imageH
                                        val cw = state.cropRect.width * imageW
                                        val ch = state.cropRect.height * imageH
                                        activeCropHandle = getCropHandle(androidx.compose.ui.geometry.Offset(offset.x, offset.y), cx, cy, cw, ch)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeCropHandle != null) {
                                            val imageW = fullW * scale
                                            val imageH = fullH * scale
                                            val newRect = applyCropDrag(
                                                state.cropRect, 
                                                activeCropHandle!!, 
                                                dragAmount.x / imageW, 
                                                dragAmount.y / imageH,
                                                state.cropAspectRatio,
                                                fullW,
                                                fullH
                                            )
                                            viewModel.updateState { it.copy(cropRect = newRect) }
                                        }
                                    },
                                    onDragEnd = {
                                        activeCropHandle = null
                                        viewModel.commitState()
                                    }
                                )
                            } else if (currentTab == "DRAW") {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val x = offset.x / renderWidth
                                        val y = offset.y / renderHeight
                                        viewModel.startDrawing(PointF(x, y))
                                    },
                                    onDrag = { change, _ ->
                                        val x = change.position.x / renderWidth
                                        val y = change.position.y / renderHeight
                                        viewModel.addDrawingPoint(PointF(x, y))
                                    },
                                    onDragEnd = { viewModel.endDrawing() }
                                )
                            }
                        }) {
                            val imageW = fullW * scale
                            val imageH = fullH * scale
                            val offsetX = if (isCropMode) 0f else -state.cropRect.left * imageW
                            val offsetY = if (isCropMode) 0f else -state.cropRect.top * imageH
                            
                            withTransform({
                                translate(left = offsetX, top = offsetY)
                                translate(left = imageW / 2f, top = imageH / 2f)
                                scale(scaleX = if (state.flipHorizontal) -1f else 1f, scaleY = if (state.flipVertical) -1f else 1f)
                                rotate(degrees = angle)
                                scale(scaleX = scale, scaleY = scale)
                                translate(left = -originalW / 2f, top = -originalH / 2f)
                            }) {
                                drawImage(
                                    image = originalBitmap!!.asImageBitmap(),
                                    colorFilter = ColorFilter.colorMatrix(state.toComposeColorMatrix())
                                )
                            }
                            
                            val contentOffsetX = if (isCropMode) state.cropRect.left * imageW else 0f
                            val contentOffsetY = if (isCropMode) state.cropRect.top * imageH else 0f
                            val contentW = if (isCropMode) state.cropRect.width * imageW else renderWidth
                            val contentH = if (isCropMode) state.cropRect.height * imageH else renderHeight
                            
                            for (drawing in state.drawings) {
                                val path = androidx.compose.ui.graphics.Path()
                                if (drawing.path.isNotEmpty()) {
                                    val start = drawing.path.first()
                                    path.moveTo(contentOffsetX + start.x * contentW, contentOffsetY + start.y * contentH)
                                    for (i in 1 until drawing.path.size) {
                                        val p = drawing.path[i]
                                        path.lineTo(contentOffsetX + p.x * contentW, contentOffsetY + p.y * contentH)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(drawing.color),
                                    style = Stroke(width = drawing.strokeWidth * contentW)
                                )
                            }
                            
                            if (isCropMode) {
                                val cx = state.cropRect.left * imageW
                                val cy = state.cropRect.top * imageH
                                val cw = state.cropRect.width * imageW
                                val ch = state.cropRect.height * imageH
                                
                                val dimPath = androidx.compose.ui.graphics.Path().apply {
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, imageW, imageH))
                                    addRect(androidx.compose.ui.geometry.Rect(cx, cy, cx + cw, cy + ch))
                                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                }
                                drawPath(dimPath, color = Color.Black.copy(alpha = 0.6f))
                                
                                drawRect(
                                    color = Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(cx, cy),
                                    size = androidx.compose.ui.geometry.Size(cw, ch),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                
                                val hs = 20.dp.toPx()
                                val ts = 4.dp.toPx()
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx + hs, cy), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx, cy + hs), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + cw, cy), androidx.compose.ui.geometry.Offset(cx + cw - hs, cy), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + cw, cy), androidx.compose.ui.geometry.Offset(cx + cw, cy + hs), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy + ch), androidx.compose.ui.geometry.Offset(cx + hs, cy + ch), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy + ch), androidx.compose.ui.geometry.Offset(cx, cy + ch - hs), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + cw, cy + ch), androidx.compose.ui.geometry.Offset(cx + cw - hs, cy + ch), strokeWidth = ts)
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + cw, cy + ch), androidx.compose.ui.geometry.Offset(cx + cw, cy + ch - hs), strokeWidth = ts)
                            }
                        }
                        
                        val contentOffsetX = if (isCropMode) state.cropRect.left * (fullW * scale) else 0f
                        val contentOffsetY = if (isCropMode) state.cropRect.top * (fullH * scale) else 0f
                        val contentW = if (isCropMode) state.cropRect.width * (fullW * scale) else renderWidth
                        val contentH = if (isCropMode) state.cropRect.height * (fullH * scale) else renderHeight
                        
                        for (text in state.texts) {
                            Text(
                                text = text.text,
                                color = Color(text.color),
                                fontSize = (text.size * contentW / density.density).sp,
                                modifier = Modifier
                                    .offset { IntOffset((contentOffsetX + text.x * contentW).toInt(), (contentOffsetY + text.y * contentH).toInt()) }
                                    .graphicsLayer(rotationZ = text.rotation)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = { viewModel.commitState() }
                                        ) { change, dragAmount -> 
                                            change.consume()
                                            viewModel.moveText(text.id, dragAmount.x / contentW, dragAmount.y / contentH)
                                        }
                                    }
                            )
                        }
                        
                        for (sticker in state.stickers) {
                            Text(
                                text = sticker.emoji,
                                fontSize = (sticker.scale * contentW / density.density).sp,
                                modifier = Modifier
                                    .offset { IntOffset((contentOffsetX + sticker.x * contentW).toInt(), (contentOffsetY + sticker.y * contentH).toInt()) }
                                    .graphicsLayer(rotationZ = sticker.rotation)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = { viewModel.commitState() }
                                        ) { change, dragAmount -> 
                                            change.consume()
                                            viewModel.moveSticker(sticker.id, dragAmount.x / contentW, dragAmount.y / contentH)
                                        }
                                    }
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun EditorBottomBar(currentTab: String, onTabSelected: (String) -> Unit, viewModel: PhotoEditorViewModel, state: EditorState, fullW: Float, fullH: Float) {
    Column(modifier = Modifier.background(Color.DarkGray)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp), contentAlignment = Alignment.Center) {
            when (currentTab) {
                "ADJUST" -> {
                    LazyRow {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.editor_brightness), color = Color.White, fontSize = 12.sp)
                                Slider(value = state.brightness, onValueChange = { viewModel.updateState { s -> s.copy(brightness = it) } }, onValueChangeFinished = { viewModel.commitState() }, valueRange = -255f..255f, modifier = Modifier.width(100.dp))
                            }
                        }
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.editor_contrast), color = Color.White, fontSize = 12.sp)
                                Slider(value = state.contrast, onValueChange = { viewModel.updateState { s -> s.copy(contrast = it) } }, onValueChangeFinished = { viewModel.commitState() }, valueRange = 0f..2f, modifier = Modifier.width(100.dp))
                            }
                        }
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.editor_saturation), color = Color.White, fontSize = 12.sp)
                                Slider(value = state.saturation, onValueChange = { viewModel.updateState { s -> s.copy(saturation = it) } }, onValueChangeFinished = { viewModel.commitState() }, valueRange = 0f..2f, modifier = Modifier.width(100.dp))
                            }
                        }
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.editor_temperature), color = Color.White, fontSize = 12.sp)
                                Slider(value = state.temperature, onValueChange = { viewModel.updateState { s -> s.copy(temperature = it) } }, onValueChangeFinished = { viewModel.commitState() }, valueRange = -1f..1f, modifier = Modifier.width(100.dp))
                            }
                        }
                    }
                }
                "CROP" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LazyRow(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            item {
                                Button(onClick = { viewModel.updateState { it.copy(rotation = it.rotation + 90f) }; viewModel.commitState() }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.editor_rotate_90)) }
                            }
                            item {
                                Button(onClick = { viewModel.updateState { it.copy(flipHorizontal = !it.flipHorizontal) }; viewModel.commitState() }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.editor_flip_h)) }
                            }
                            item {
                                Button(onClick = { viewModel.updateState { it.copy(flipVertical = !it.flipVertical) }; viewModel.commitState() }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.editor_flip_v)) }
                            }
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.editor_straighten), color = Color.White, fontSize = 12.sp)
                                    Slider(value = state.straighten, onValueChange = { viewModel.updateState { s -> s.copy(straighten = it) } }, onValueChangeFinished = { viewModel.commitState() }, valueRange = -45f..45f, modifier = Modifier.width(100.dp))
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            val ratios = listOf(
                                "Free" to null,
                                "1:1" to 1f,
                                "4:3" to 4f/3f,
                                "16:9" to 16f/9f,
                                "3:4" to 3f/4f,
                                "9:16" to 9f/16f
                            )
                            items(ratios) { (name, ratio) ->
                                TextButton(onClick = {
                                    val newRect = if (ratio != null) {
                                        setCropAspectRatio(ratio, state.cropRect, fullW, fullH)
                                    } else {
                                        state.cropRect
                                    }
                                    viewModel.updateState { it.copy(cropAspectRatio = ratio, cropRect = newRect) }
                                    viewModel.commitState()
                                }) {
                                    Text(name, color = if (state.cropAspectRatio == ratio) Color.Yellow else Color.White)
                                }
                            }
                        }
                    }
                }
                "FILTERS" -> {
                    LazyRow {
                        items(FilterType.values()) { filter ->
                            Button(onClick = { viewModel.updateState { it.copy(filter = filter) }; viewModel.commitState() }, modifier = Modifier.padding(4.dp)) {
                                Text(filter.name)
                            }
                        }
                    }
                }
                "TEXT" -> {
                    Button(onClick = { viewModel.addText("HELLO") }) {
                        Text(stringResource(R.string.editor_add_text))
                    }
                }
                "DRAW" -> {
                    Text("Drag on image to draw", color = Color.White)
                }
                "STICKER" -> {
                    LazyRow {
                        items(listOf("😀", "❤️", "🔥", "🌟", "🎉", "✨", "😎", "🐱")) { emoji ->
                            Text(text = emoji, fontSize = 32.sp, modifier = Modifier
                                .padding(8.dp)
                                .clickable { viewModel.addSticker(emoji) })
                        }
                    }
                }
                "AI" -> {
                    val aiProgress by viewModel.aiProgress.collectAsState()
                    val aiError by viewModel.aiError.collectAsState()
                    val previewAiUri by viewModel.previewAiResultUri.collectAsState()
                    
                    if (aiProgress != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            Text(aiProgress?.statusMessage ?: stringResource(R.string.ai_processing), color = Color.White)
                        }
                    } else if (previewAiUri != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Previewing AI Result", color = Color.Yellow)
                            Row {
                                Button(onClick = { viewModel.acceptAiResult() }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_apply)) }
                                Button(onClick = { viewModel.discardAiResult() }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_cancel)) }
                            }
                        }
                    } else {
                        LazyRow(modifier = Modifier.fillMaxWidth()) {
                            item { Button(onClick = { viewModel.processAITool(AIRequest.BackgroundRemoval(state.uriString)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_background_removal)) } }
                            item { Button(onClick = { viewModel.processAITool(AIRequest.DetectObjects(state.uriString)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_object_detection)) } }

                            item { Button(onClick = { viewModel.processAITool(AIRequest.Enhance(state.uriString, "auto")) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_enhance)) } }
                            item { Button(onClick = { viewModel.processAITool(AIRequest.Upscale(state.uriString, 2)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_upscale)) } }
                            item { 
                                var prompt by remember { mutableStateOf("") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = prompt,
                                        onValueChange = { prompt = it },
                                        label = { Text(stringResource(R.string.ai_prompt_hint), color = Color.White) },
                                        modifier = Modifier.width(200.dp).padding(4.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                        singleLine = true
                                    )
                                    Button(onClick = { if (prompt.isNotBlank()) viewModel.processAssistantInstruction(prompt) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_assistant)) }
                                }
                            }
                        }
                        if (aiError != null) {
                            Text(aiError!!, color = Color.Red, modifier = Modifier.padding(4.dp))
                        }
                    }
                }


            }
        }
        
        ScrollableTabRow(
            selectedTabIndex = listOf("ADJUST", "FILTERS", "CROP", "TEXT", "DRAW", "STICKER", "AI").indexOf(currentTab),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            edgePadding = 8.dp
        ) {
            val tabs = listOf(
                "ADJUST" to R.string.editor_adjust,
                "FILTERS" to R.string.editor_filters,
                "CROP" to R.string.editor_crop,
                "TEXT" to R.string.editor_text,
                "DRAW" to R.string.editor_draw,
                "STICKER" to R.string.editor_sticker,
                "AI" to R.string.ai_tools
            )
            tabs.forEach { (key, titleRes) ->
                Tab(
                    selected = currentTab == key,
                    onClick = { onTabSelected(key) },
                    text = { Text(stringResource(titleRes)) }
                )
            }
        }
    }
}

fun getCropHandle(pos: androidx.compose.ui.geometry.Offset, cx: Float, cy: Float, cw: Float, ch: Float): CropHandle? {
    val touchSlop = 40f
    val l = cx
    val t = cy
    val r = cx + cw
    val b = cy + ch
    
    fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < touchSlop
    
    return when {
        near(pos.x, l) && near(pos.y, t) -> CropHandle.TL
        near(pos.x, r) && near(pos.y, t) -> CropHandle.TR
        near(pos.x, l) && near(pos.y, b) -> CropHandle.BL
        near(pos.x, r) && near(pos.y, b) -> CropHandle.BR
        near(pos.x, l) && pos.y in t..b -> CropHandle.L
        near(pos.x, r) && pos.y in t..b -> CropHandle.R
        near(pos.y, t) && pos.x in l..r -> CropHandle.T
        near(pos.y, b) && pos.x in l..r -> CropHandle.B
        pos.x in l..r && pos.y in t..b -> CropHandle.CENTER
        else -> null
    }
}

fun applyCropDrag(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, ratio: Float?, fullW: Float, fullH: Float): CropRect {
    var l = rect.left
    var t = rect.top
    var r = rect.right
    var b = rect.bottom
    
    if (handle == CropHandle.CENTER) {
        l += dx; r += dx; t += dy; b += dy
        if (l < 0f) { r -= l; l = 0f }
        if (t < 0f) { b -= t; t = 0f }
        if (r > 1f) { l -= (r - 1f); r = 1f }
        if (b > 1f) { t -= (b - 1f); b = 1f }
        return CropRect(l, t, r, b)
    }
    
    when (handle) {
        CropHandle.TL -> { l += dx; t += dy }
        CropHandle.TR -> { r += dx; t += dy }
        CropHandle.BL -> { l += dx; b += dy }
        CropHandle.BR -> { r += dx; b += dy }
        CropHandle.T -> t += dy
        CropHandle.B -> b += dy
        CropHandle.L -> l += dx
        CropHandle.R -> r += dx
        else -> {}
    }
    
    val minSize = 0.05f
    if (l > r - minSize) { if (handle.name.contains("L")) l = r - minSize else r = l + minSize }
    if (t > b - minSize) { if (handle.name.contains("T")) t = b - minSize else b = t + minSize }
    
    if (ratio != null) {
        val normalizedRatio = ratio * fullH / fullW
        val w = r - l
        val h = b - t
        if (handle == CropHandle.R || handle == CropHandle.L) {
            val expectedH = w / normalizedRatio
            val center = (t + b) / 2
            t = center - expectedH / 2
            b = center + expectedH / 2
        } else if (handle == CropHandle.T || handle == CropHandle.B) {
            val expectedW = h * normalizedRatio
            val center = (l + r) / 2
            l = center - expectedW / 2
            r = center + expectedW / 2
        } else {
            val expectedH = w / normalizedRatio
            if (handle == CropHandle.TL || handle == CropHandle.TR) t = b - expectedH
            else b = t + expectedH
        }
    }
    
    l = l.coerceIn(0f, 1f)
    t = t.coerceIn(0f, 1f)
    r = r.coerceIn(l + minSize, 1f)
    b = b.coerceIn(t + minSize, 1f)
    
    if (ratio != null) {
        val normalizedRatio = ratio * fullH / fullW
        val w = r - l
        val expectedH = w / normalizedRatio
        if (expectedH <= b - t) {
            if (handle == CropHandle.TL || handle == CropHandle.TR) t = b - expectedH
            else b = t + expectedH
        } else {
            val h = b - t
            val expectedW = h * normalizedRatio
            if (handle == CropHandle.TL || handle == CropHandle.BL) l = r - expectedW
            else r = l + expectedW
        }
    }
    
    return CropRect(l, t, r, b)
}

fun setCropAspectRatio(ratio: Float, currentCrop: CropRect, fullW: Float, fullH: Float): CropRect {
    val normalizedRatio = ratio * fullH / fullW
    val cx = (currentCrop.left + currentCrop.right) / 2
    val cy = (currentCrop.top + currentCrop.bottom) / 2
    var cw = currentCrop.width
    var ch = cw / normalizedRatio
    
    if (ch > 1f) {
        ch = 1f
        cw = ch * normalizedRatio
    }
    if (cw > 1f) {
        cw = 1f
        ch = cw / normalizedRatio
    }
    
    var l = cx - cw / 2
    var t = cy - ch / 2
    var r = cx + cw / 2
    var b = cy + ch / 2
    
    if (l < 0f) { r -= l; l = 0f }
    if (t < 0f) { b -= t; t = 0f }
    if (r > 1f) { l -= (r - 1f); r = 1f }
    if (b > 1f) { t -= (b - 1f); b = 1f }
    
    return CropRect(l, t, r, b)
}