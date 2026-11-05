package com.example.ai.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.core.AIProviderType
import com.example.ai.model.*
import com.example.ai.generative.CloudGenerativeClient
import com.example.ai.generative.BackendHealth
import com.example.ai.generative.GenerativeEngine
import com.example.ai.generative.GenerativeRequest
import com.example.ai.generative.GenerativeResult
import com.example.ai.generative.GenerativeType
import com.example.ai.generative.JobState
import com.example.ai.provider.AIProviderManager
import com.example.ui.components.AppTopBar
import com.example.ui.components.GlassSurface
import com.example.ui.components.SectionHeader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOCAL_AI_MODEL_IDS = listOf(
    "llama/inpainting_lama_2025jan",
    "realesrgan_x2plus",
    "cpga_fp16"
)

private fun localModelIdFor(capability: AICapability): String? = when (capability) {
    AICapability.OBJECT_REMOVAL -> "llama/inpainting_lama_2025jan"
    AICapability.UPSCALE -> "realesrgan_x2plus"
    AICapability.ENHANCEMENT -> "cpga_fp16"
    else -> null
}

@Composable
fun AIToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val providerManager = remember { AIProviderManager(context) }
    val modelManager = remember { AppModelManager(context) }
    val modelStates by modelManager.artifacts.states.collectAsState()
    val trellisEngine = remember { GenerativeEngine(context.applicationContext) }
    val cloudClient = remember { CloudGenerativeClient(context.applicationContext) }
    var backendHealth by remember { mutableStateOf<BackendHealth?>(null) }
    var checkingBackend by remember { mutableStateOf(false) }
    var activatingModels by remember { mutableStateOf(false) }
    var activationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cloudClient) {
        checkingBackend = true
        backendHealth = cloudClient.checkBackendHealth()
        checkingBackend = false
    }
    val trellisJobs by trellisEngine.jobs.collectAsState()
    var trellisImageUri by remember { mutableStateOf<Uri?>(null) }
    var activeTrellisJobId by remember { mutableStateOf<String?>(null) }
    var showTrellisConsent by remember { mutableStateOf(false) }
    val activeTrellisJob = activeTrellisJobId?.let { trellisJobs[it] }
    val trellisOutputUri = (activeTrellisJob?.result as? GenerativeResult.Success)?.outputUri
    val trellisBusy = activeTrellisJob?.state?.let {
        it in setOf(JobState.QUEUED, JobState.PREPARING, JobState.UPLOADING, JobState.PROCESSING, JobState.DOWNLOADING)
    } == true

    LaunchedEffect(modelManager) {
        withContext(Dispatchers.IO) {
            modelManager.artifacts.refreshInstalledStates()
        }
    }

    val pickTrellisImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> trellisImageUri = uri }

    val saveTrellisModel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("model/gltf-binary")
    ) { destination ->
        val source = trellisOutputUri
        if (destination != null && source != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val saved = runCatching {
                    val input = context.contentResolver.openInputStream(source)
                        ?: error("Cannot read generated 3D model")
                    val output = context.contentResolver.openOutputStream(destination)
                        ?: error("Cannot open destination file")
                    input.use { sourceStream -> output.use { targetStream -> sourceStream.copyTo(targetStream) } }
                }.isSuccess
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(if (saved) R.string.trellis_save_success else R.string.trellis_save_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val isLocalAvailable = remember { providerManager.getProvider(AIProviderType.ON_DEVICE).isAvailable }
    val isCloudConfigured = providerManager.getProvider(AIProviderType.CLOUD).isAvailable
    val backendCanAcceptRequests = backendHealth?.reachable == true &&
        backendHealth?.backendAuthConfigured == true && backendHealth?.publicBaseUrlConfigured == true
    val isGeminiAvailable = isCloudConfigured && backendCanAcceptRequests &&
        backendHealth?.geminiConfigured == true
    val isTrellisAvailable = isCloudConfigured && backendCanAcceptRequests &&
        backendHealth?.trellisConfigured == true
    val isCloudAvailable = isGeminiAvailable

    val models = remember(modelStates) { modelManager.getAvailableModels() }
    val localModels = remember(models) {
        models.filter { it.id in LOCAL_AI_MODEL_IDS }
    }
    val allLocalReady = localModels.size == LOCAL_AI_MODEL_IDS.size &&
        localModels.all { it.status == ModelInstallState.READY }
    val anyLocalBusy = localModels.any {
        it.status == ModelInstallState.DOWNLOADING || it.status == ModelInstallState.VERIFYING
    }

    fun activateAllLocalModels() {
        if (activatingModels) return
        coroutineScope.launch {
            activatingModels = true
            activationMessage = null
            val results = LOCAL_AI_MODEL_IDS.map { id ->
                async(Dispatchers.IO) { id to modelManager.downloadModel(id) }
            }.awaitAll()
            val failed = results.filter { it.second.isFailure }.map { it.first }
            activationMessage = if (failed.isEmpty()) {
                "All three downloadable on-device models are installed and ready."
            } else {
                "Some models could not be activated: " + failed.joinToString()
            }
            activatingModels = false
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.ai_tools),
                onBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "AI Providers")
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProviderBadge(name = "On-Device", isAvailable = isLocalAvailable)
                        ProviderBadge(name = if (checkingBackend) "Cloud • Checking" else "Cloud", isAvailable = isCloudAvailable)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = when {
                            backendHealth == null && checkingBackend -> "Checking AI backend connection…"
                            backendHealth == null -> "Cloud backend is unreachable or its URL is not configured."
                            !backendHealth.backendAuthConfigured -> "Backend access token is not configured."
                            !backendHealth.publicBaseUrlConfigured -> "Backend public HTTPS URL is not configured."
                            else -> "Gemini: " + (if (backendHealth.geminiConfigured) "configured" else "missing key") +
                                " • TRELLIS: " + (if (backendHealth.trellisConfigured) "configured" else "missing key")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCloudAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                checkingBackend = true
                                backendHealth = cloudClient.checkBackendHealth()
                                checkingBackend = false
                            }
                        },
                        enabled = !checkingBackend
                    ) { Text(if (checkingBackend) "Checking…" else "Refresh connection") }
                }
            }

            item {
                TrellisToolCard(
                    imageSelected = trellisImageUri != null,
                    cloudAvailable = isTrellisAvailable,
                    busy = trellisBusy,
                    progress = activeTrellisJob?.progress ?: 0f,
                    status = activeTrellisJob?.message.orEmpty(),
                    failed = activeTrellisJob?.state == JobState.FAILED,
                    canSave = trellisOutputUri != null,
                    onPickImage = { pickTrellisImage.launch("image/*") },
                    onGenerate = { showTrellisConsent = true },
                    onSave = { saveTrellisModel.launch("trellis-model.glb") }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "On-Device AI Models")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = ::activateAllLocalModels,
                    enabled = !allLocalReady && !anyLocalBusy && !activatingModels,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(when {
                        allLocalReady -> "On-Device AI Activated"
                        activatingModels -> "Activating models…"
                        else -> "Activate 3 On-Device AI Tools"
                    })
                }
                if (activatingModels) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                activationMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allLocalReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloads the three optional on-device models once. ML Kit tools are included separately; cloud tools need working provider credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(models.filter { it.runtime != "ML Kit" }) { model ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${model.description} (${formatSize(model.expectedBytes ?: model.sizeBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (model.license != null || model.runtime != null) {
                                    Text(
                                        text = "${model.runtime ?: ""} • ${model.license ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            val (statusText, statusColor) = when (model.status) {
                                ModelInstallState.READY -> "Ready" to MaterialTheme.colorScheme.tertiary
                                ModelInstallState.DOWNLOADING -> "Downloading ${model.progress}%" to MaterialTheme.colorScheme.primary
                                ModelInstallState.VERIFYING -> "Verifying..." to MaterialTheme.colorScheme.secondary
                                ModelInstallState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                                ModelInstallState.NOT_INSTALLED -> "Not Installed" to MaterialTheme.colorScheme.outline
                            }

                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (model.status == ModelInstallState.DOWNLOADING) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { model.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (model.status == ModelInstallState.VERIFYING) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (model.errorMessage != null && model.status == ModelInstallState.FAILED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = model.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (model.status) {
                                ModelInstallState.NOT_INSTALLED, ModelInstallState.FAILED -> {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                modelManager.downloadModel(model.id)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (model.status == ModelInstallState.FAILED) "Retry" else "Download")
                                    }
                                }
                                ModelInstallState.READY -> {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                modelManager.deleteModel(model.id)
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete")
                                    }
                                }
                                ModelInstallState.DOWNLOADING, ModelInstallState.VERIFYING -> {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "AI Capabilities")
            }

            val tools = listOf(
                Triple(R.string.ai_background_removal, AICapability.BACKGROUND_REMOVAL, Icons.Default.PersonRemove),
                Triple(R.string.ai_object_detection, AICapability.OBJECT_DETECTION, Icons.Default.Search),
                Triple(R.string.ai_object_removal, AICapability.OBJECT_REMOVAL, Icons.Default.DeleteSweep),
                Triple(R.string.ai_upscale, AICapability.UPSCALE, Icons.Default.ZoomIn),
                Triple(R.string.ai_enhance, AICapability.ENHANCEMENT, Icons.Default.AutoFixHigh),
                Triple(R.string.ai_restyle, AICapability.RESTYLE, Icons.Default.Brush),
                Triple(R.string.ai_gen_fill, AICapability.GENERATIVE_FILL, Icons.Default.FormatPaint),
                Triple(R.string.ai_img_to_img, AICapability.IMAGE_TO_IMAGE, Icons.Default.Transform),
                Triple(R.string.ai_object_tracking, AICapability.VIDEO_OBJECT_TRACKING, Icons.Default.Search),
                Triple(R.string.ai_smart_cut, AICapability.SMART_CUT, Icons.Default.AutoFixHigh),
                Triple(R.string.ai_auto_captions, AICapability.AUTO_CAPTIONS, Icons.Default.Subtitles),
                Triple(R.string.ai_video_enhance, AICapability.VIDEO_ENHANCEMENT, Icons.Default.AutoFixHigh),
                Triple(R.string.ai_smart_reframe, AICapability.SMART_REFRAME, Icons.Default.Crop),
                Triple(R.string.ai_img_to_vid, AICapability.IMAGE_TO_VIDEO, Icons.Default.Movie),
                Triple(R.string.ai_vid_to_vid, AICapability.VIDEO_TO_VIDEO, Icons.Default.Movie),
                Triple(R.string.ai_vid_ext, AICapability.VIDEO_EXTENSION, Icons.Default.Movie)
            )

            items(tools.size) { index ->
                val (titleRes, capability, icon) = tools[index]
                val baseStatus = modelManager.getCapabilityStatus(capability)
                val modelId = localModelIdFor(capability)
                val modelState = modelId?.let { modelStates[it] }
                val mappedStatus = when {
                    modelState?.state == ModelInstallState.DOWNLOADING -> "Downloading ${modelState.progress}%" to MaterialTheme.colorScheme.primary
                    modelState?.state == ModelInstallState.VERIFYING -> "Verifying..." to MaterialTheme.colorScheme.secondary
                    modelState?.state == ModelInstallState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                    else -> when (baseStatus) {
                        AICapabilityStatus.AVAILABLE -> "Ready" to MaterialTheme.colorScheme.tertiary
                        AICapabilityStatus.UNAVAILABLE -> "Unavailable" to MaterialTheme.colorScheme.error
                        AICapabilityStatus.REQUIRES_MODEL -> "Download Required" to MaterialTheme.colorScheme.secondary
                        AICapabilityStatus.REQUIRES_CLOUD -> if (isGeminiAvailable) {
                            "Cloud Ready" to MaterialTheme.colorScheme.tertiary
                        } else {
                            "Cloud Setup Required" to MaterialTheme.colorScheme.error
                        }
                        AICapabilityStatus.DEVICE_NOT_SUPPORTED -> "Not Supported" to MaterialTheme.colorScheme.error
                        AICapabilityStatus.REQUIRES_PROVIDER -> "Setup Required" to MaterialTheme.colorScheme.error
                        AICapabilityStatus.PROCESSING -> "Processing..." to MaterialTheme.colorScheme.primary
                        AICapabilityStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                        AICapabilityStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                val (statusText, statusColor) = mappedStatus
                val isBusy = modelState?.state == ModelInstallState.DOWNLOADING || modelState?.state == ModelInstallState.VERIFYING
                val canActivate = modelId != null && baseStatus == AICapabilityStatus.REQUIRES_MODEL && !isBusy
                val actionText = if (modelState?.state == ModelInstallState.FAILED) "Retry" else "Activate"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    stringResource(titleRes),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Surface(
                                color = statusColor.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (canActivate) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        modelManager.downloadModel(requireNotNull(modelId))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(actionText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTrellisConsent) {
        AlertDialog(
            onDismissRequest = { showTrellisConsent = false },
            title = { Text(stringResource(R.string.trellis_consent_title)) },
            text = { Text(stringResource(R.string.trellis_consent_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrellisConsent = false
                        val source = trellisImageUri
                        if (source != null) {
                            activeTrellisJobId = trellisEngine.submitJob(
                                GenerativeRequest(
                                    type = GenerativeType.TRELLIS_IMAGE_TO_3D,
                                    sourceUri = source,
                                    prompt = "Generate a textured 3D mesh from this image.",
                                    parameters = mapOf(
                                        "texture_size" to 1024,
                                        "mesh_simplify" to 0.95,
                                        "ss_sampling_steps" to 12,
                                        "slat_sampling_steps" to 12
                                    )
                                )
                            )
                        }
                    }
                ) { Text(stringResource(R.string.trellis_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showTrellisConsent = false }) {
                    Text(stringResource(R.string.trellis_cancel))
                }
            }
        )
    }
}

@Composable
private fun TrellisToolCard(
    imageSelected: Boolean,
    cloudAvailable: Boolean,
    busy: Boolean,
    progress: Float,
    status: String,
    failed: Boolean,
    canSave: Boolean,
    onPickImage: () -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.trellis_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.trellis_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(if (imageSelected) R.string.trellis_image_selected else R.string.trellis_choose_image))
            }
            if (!cloudAvailable) {
                Text(
                    stringResource(R.string.trellis_cloud_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onGenerate,
                enabled = imageSelected && cloudAvailable && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.trellis_generate))
            }
            if (busy) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(status.ifBlank { stringResource(R.string.trellis_generating) }, style = MaterialTheme.typography.bodySmall)
            } else if (canSave) {
                Text(stringResource(R.string.trellis_done), color = MaterialTheme.colorScheme.tertiary)
                OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.trellis_save))
                }
            } else if (failed) {
                Text(
                    status.ifBlank { stringResource(R.string.ai_error_generic) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (!imageSelected) {
                Text(
                    stringResource(R.string.trellis_need_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "Unknown size"
    return if (bytes >= 1024 * 1024) {
        String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    } else {
        String.format("%d KB", bytes / 1024)
    }
}

@Composable
fun ProviderBadge(name: String, isAvailable: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}
