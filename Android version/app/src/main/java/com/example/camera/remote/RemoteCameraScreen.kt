package com.example.camera.remote

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.EnumMap

@Composable
fun RemoteCameraScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var address by remember { mutableStateOf("") }
    var activeAddress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerState by remember { mutableStateOf("idle") }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents?.trim().orEmpty()
        if (scanned.isNotEmpty()) {
            val candidate = scanned.substringAfter("MEDIAAI-RTSP:", scanned).trim()
            if (isSupportedStreamAddress(candidate)) {
                address = candidate
                error = null
            } else {
                error = "رمز QR لا يحتوي على عنوان بث RTSP صالح."
            }
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    playerState = when (playbackState) {
                        Player.STATE_BUFFERING -> "buffering"
                        Player.STATE_READY -> "ready"
                        Player.STATE_ENDED -> "ended"
                        else -> "idle"
                    }
                }

                override fun onPlayerError(exception: PlaybackException) {
                    playerState = "error"
                    error = "تعذر تشغيل البث. تحقق من العنوان وبيانات الدخول وإمكانية الوصول للكاميرا."
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(activeAddress) {
        val stream = activeAddress ?: return@LaunchedEffect
        error = null
        playerState = "buffering"
        player.setMediaItem(MediaItem.fromUri(Uri.parse(stream)))
        player.prepare()
        player.playWhenReady = true
    }

    val qrBitmap = remember(address) { address.takeIf { isSupportedStreamAddress(it) }?.let(::makeQrBitmap) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("كاميرا عن بُعد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "اربط كاميرا IP أو هاتفًا آخر يعمل كتطبيق بث RTSP. يعمل الاتصال محليًا أو عبر الإنترنت إذا كان عنوان البث متاحًا من جهازك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Text("إضافة مصدر كاميرا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("عنوان البث RTSP") },
                    placeholder = { Text("rtsp://192.168.1.50:554/stream1") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val candidate = address.trim()
                            if (isSupportedStreamAddress(candidate)) {
                                activeAddress = candidate
                                error = null
                            } else error = "أدخل عنوانًا يبدأ بـ rtsp:// أو rtsps://"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("اتصال بالكاميرا") }
                    OutlinedButton(onClick = {
                        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("امسح QR الخاص بعنوان بث الكاميرا").setBeepEnabled(false).setOrientationLocked(false))
                    }) {
                        Icon(Icons.Default.QrCode2, contentDescription = null)
                        Text("مسح QR")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        qrBitmap?.let { bitmap ->
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Text("رمز QR لمشاركة عنوان البث", fontWeight = FontWeight.SemiBold)
                    Image(bitmap.asImageBitmap(), contentDescription = "QR code for RTSP stream", modifier = Modifier.size(210.dp))
                    Text("امسح الرمز من جهاز آخر لاستيراد العنوان داخل MediaAIStudio.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(address.trim())) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("نسخ عنوان البث")
                    }
                    Text("تنبيه: قد يحتوي الرابط على اسم مستخدم وكلمة مرور الكاميرا. لا تشارك الرمز إلا مع أشخاص موثوقين.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (activeAddress != null) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Text("البث المباشر", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { player.stop(); activeAddress = null; playerState = "idle" }) { Text("إيقاف") }
                    }
                    AndroidView(
                        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true } },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(12.dp))
                    )
                    if (playerState == "buffering") Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("جارٍ الاتصال بالكاميرا…", modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(activeAddress.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()
        Text("الأجهزة المدعومة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "• كاميرات IP التي توفر بث RTSP.\n• هاتف أندرويد أو iPhone عند تشغيل تطبيق كاميرا شبكية يدعم RTSP وعرض عنوان البث.\n• داخل الشبكة: استخدم عنوان IP المحلي.\n• عبر الإنترنت: استخدم عنوانًا عامًا أو VPN آمنًا أو خدمة Relay/TURN متاحة للطرفين.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "ملاحظة: هذه الشاشة تستقبل بث RTSP وتشارك عنوانه عبر QR؛ لا تحوّل الهاتف تلقائيًا إلى كاميرا مرسِلة، ولا تنشئ نفقًا سحابيًا تلقائيًا. يلزم توفير RTSP من الجهاز المرسل وإتاحة الوصول إليه عبر الإنترنت للاتصال من خارج الشبكة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun isSupportedStreamAddress(value: String): Boolean {
    val candidate = value.trim()
    return (candidate.startsWith("rtsp://", ignoreCase = true) ||
        candidate.startsWith("rtsps://", ignoreCase = true)) &&
        runCatching { Uri.parse(candidate).host?.isNotBlank() == true }.getOrDefault(false)
}

private fun makeQrBitmap(value: String): Bitmap? = runCatching {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.MARGIN, 1)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
    }
    val matrix = QRCodeWriter().encode("MEDIAAI-RTSP:$value", BarcodeFormat.QR_CODE, 640, 640, hints)
    Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 640) for (y in 0 until 640) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()
