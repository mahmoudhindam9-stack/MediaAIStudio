package com.example.update.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.update.*
import com.example.ui.components.AppTopBar
import com.example.ui.components.GlassSurface
import com.example.ui.components.SectionHeader
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UpdatePreferences(context) }
    
    val autoUpdateEnabled by prefs.autoUpdateEnabled.collectAsState(initial = false)
    val lastCheckTime by prefs.lastCheckTime.collectAsState(initial = 0L)
    
    var checkState by remember { mutableStateOf<String?>(null) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var downloadState by remember { mutableStateOf<UpdateState?>(null) }
    
    val currentVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }
    
    fun toggleAutoUpdate(enabled: Boolean) {
        scope.launch {
            prefs.setAutoUpdateEnabled(enabled)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "AutoUpdateCheck",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork("AutoUpdateCheck")
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(com.example.R.string.settings_updates),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp)
        ) {
            SectionHeader(title = "App Info")
            GlassSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Text("MediaAIStudio", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(com.example.R.string.version_info, currentVersion ?: "Unknown"))
                    Text("© 2026 MediaAIStudio. All rights reserved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Update Preferences")
            GlassSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(com.example.R.string.auto_update_checks))
                            if (lastCheckTime > 0) {
                                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                Text(
                                    stringResource(com.example.R.string.last_check, format.format(Date(lastCheckTime))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = { toggleAutoUpdate(it) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                checkState = context.getString(com.example.R.string.checking_updates)
                                updateResult = null
                                downloadState = null
                                val manager = UpdateManager(context)
                                prefs.setLastCheckTime(System.currentTimeMillis())
                                val res = manager.checkForUpdate()
                                updateResult = res
                                checkState = when (res) {
                                    is UpdateCheckResult.UpToDate -> context.getString(com.example.R.string.up_to_date)
                                    is UpdateCheckResult.UpdateAvailable -> context.getString(com.example.R.string.update_available)
                                    is UpdateCheckResult.Error -> context.getString(com.example.R.string.error_checking, res.reason)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(com.example.R.string.check_now))
                    }
                    
                    if (checkState != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(checkState!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            
            if (updateResult is UpdateCheckResult.UpdateAvailable) {
                val release = (updateResult as UpdateCheckResult.UpdateAvailable).releaseInfo
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = "New Update Available")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(release.releaseName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(stringResource(com.example.R.string.version_info, release.versionName ?: "Unknown"), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(stringResource(com.example.R.string.size_mb, release.apkAssetSize / 1024 / 1024), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(release.releaseNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (downloadState == null || downloadState is UpdateState.Error) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val manager = UpdateManager(context)
                                        manager.downloadAndVerify(release).collect { state ->
                                            downloadState = state
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(com.example.R.string.download_update))
                            }
                            if (downloadState is UpdateState.Error) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text((downloadState as UpdateState.Error).reason, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            when (val st = downloadState) {
                                is UpdateState.Downloading -> {
                                    val progress = if (st.totalBytes > 0) st.bytesDownloaded.toFloat() / st.totalBytes else 0f
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                    Text("${st.bytesDownloaded / 1024} KB / ${st.totalBytes / 1024} KB", color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                is UpdateState.Verifying -> {
                                    Text(stringResource(com.example.R.string.verifying_update), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                is UpdateState.ReadyToInstall -> {
                                    Button(
                                        onClick = { UpdateManager(context).installApk(st.apkFile) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(com.example.R.string.install_update))
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
