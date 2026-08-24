package com.kmjs.virtualcamera.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.inject.TargetAppConfig
import com.kmjs.virtualcamera.rtsp.RtspConnectionState
import com.kmjs.virtualcamera.test.CameraTestActivity
import com.kmjs.virtualcamera.util.KmjsLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmjsMainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val logs by viewModel.logEntries.collectAsState()
    val context = LocalContext.current

    val tabs = listOf("Stream & Control", "Camera Test", "Target Architecture", "Logs (${logs.size})")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "KMJS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "VIRTUAL CAM",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Generic Multi-App RTSP Injection",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    ConnectionStatusBadge(state = uiState.connectionState)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    if (uiState.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("tab_$index")
                    )
                }
            }

            when (uiState.selectedTab) {
                0 -> StreamControlTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenTestActivity = {
                        context.startActivity(Intent(context, CameraTestActivity::class.java))
                    }
                )
                1 -> CameraTestTab(
                    onLaunchStandalone = {
                        context.startActivity(Intent(context, CameraTestActivity::class.java))
                    }
                )
                2 -> TargetArchitectureTab(
                    uiState = uiState,
                    viewModel = viewModel
                )
                3 -> LogsTab(logs = logs, onClear = { viewModel.clearLogs() })
            }
        }
    }
}

@Composable
fun StreamControlTab(
    uiState: KmjsUiState,
    viewModel: MainViewModel,
    onOpenTestActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // RTSP Input & Presets Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "RTSP STREAM CONFIGURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = uiState.rtspUrl,
                    onValueChange = { viewModel.setRtspUrl(it) },
                    label = { Text("RTSP Stream URL") },
                    placeholder = { Text("rtsp://host:port/path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rtsp_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Presets row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.presetUrls.forEach { preset ->
                        val isSelected = uiState.rtspUrl == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setRtspUrl(preset) },
                            label = {
                                Text(
                                    text = preset.substringAfter("://").substringBefore("/").take(22),
                                    fontSize = 11.sp
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Primary Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isConnected = uiState.connectionState == RtspConnectionState.CONNECTED
                    val isConnecting = uiState.connectionState == RtspConnectionState.CONNECTING ||
                            uiState.connectionState == RtspConnectionState.RECONNECTING

                    Button(
                        onClick = { viewModel.connect() },
                        enabled = !isConnecting && !isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("connect_button")
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONNECT", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        enabled = isConnected || isConnecting || uiState.isTestPatternActive,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("disconnect_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DISCONNECT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Video Preview Container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101216)),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LiveFrameView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Status & FPS Overlay HUD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.connectionState == RtspConnectionState.CONNECTED)
                                        Color(0xFF00E676)
                                    else
                                        Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.connectionState == RtspConnectionState.CONNECTED) "LIVE FEED" else "PREVIEW IDLE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "FPS: ${String.format("%.1f", uiState.frameStats.fps)} | ${uiState.frameStats.width.takeIf { it > 0 } ?: 1920}x${uiState.frameStats.height.takeIf { it > 0 } ?: 1080}",
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bottom State Message Overlay
                if (uiState.connectionState != RtspConnectionState.CONNECTED && !uiState.isTestPatternActive) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.statusMessage,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.toggleTestPattern() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_test_pattern_button")
                        ) {
                            Text("Start Test Pattern Generator (Offline Mode)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Stream Metrics & Statistics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "FPS",
                value = String.format("%.1f", uiState.frameStats.fps),
                unit = "fps",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Resolution",
                value = "${uiState.frameStats.width.takeIf { it > 0 } ?: 1920}x${uiState.frameStats.height.takeIf { it > 0 } ?: 1080}",
                unit = "px",
                modifier = Modifier.weight(1.3f)
            )
            MetricCard(
                title = "Frames",
                value = "${uiState.frameStats.totalFramesDecoded}",
                unit = "decoded",
                modifier = Modifier.weight(1f)
            )
        }

        // Background Service & IPC Status Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOREGROUND SERVICE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.isServiceRunning) "ACTIVE (Survives minimize)" else "STOPPED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isServiceRunning) Color(0xFF00E676) else Color.Gray
                    )
                }
                Text(
                    text = "The RTSP service continuously decodes video and feeds the FrameProvider. Minimizing or opening other apps will not interrupt the stream.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onOpenTestActivity,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("launch_camera_test_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Camera Test Target", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveFrameView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        val consumer = object : com.kmjs.virtualcamera.frame.FrameConsumer {
            override fun onFrameAvailable(frame: VideoFrame) {
                if (frame.bitmap != null) {
                    currentBitmap = frame.bitmap
                }
            }
        }
        KmjsFrameManager.registerConsumer(consumer)
        onDispose {
            KmjsFrameManager.unregisterConsumer(consumer)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.attachPreviewSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.detachPreviewSurface()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        currentBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Live Decoded RTSP Feed",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ConnectionStatusBadge(state: RtspConnectionState) {
    val (bgColor, textColor, text) = when (state) {
        RtspConnectionState.CONNECTED -> Triple(Color(0xFF00E676).copy(alpha = 0.15f), Color(0xFF00E676), "CONNECTED")
        RtspConnectionState.CONNECTING -> Triple(Color(0xFF29B6F6).copy(alpha = 0.15f), Color(0xFF29B6F6), "CONNECTING")
        RtspConnectionState.RECONNECTING -> Triple(Color(0xFFFFB300).copy(alpha = 0.15f), Color(0xFFFFB300), "RETRYING")
        RtspConnectionState.ERROR -> Triple(Color(0xFFFF5252).copy(alpha = 0.15f), Color(0xFFFF5252), "ERROR")
        RtspConnectionState.DISCONNECTED -> Triple(Color.Gray.copy(alpha = 0.15f), Color.LightGray, "DISCONNECTED")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CameraTestTab(
    onLaunchStandalone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "TARGET CAMERA VERIFICATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "KMJS includes a target camera test screen to verify physical camera vs RTSP virtual camera substitution. When running normally, it shows the physical camera; when hooked with KMJS injection, it receives the RTSP video stream.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onLaunchStandalone,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_camera_test_full_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Camera Viewfinder Target")
                }
            }
        }
    }
}

@Composable
fun TargetArchitectureTab(
    uiState: KmjsUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var newPkgInput by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Module Hook Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isModuleHooked) Color(0xFF003820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isModuleHooked) Icons.Default.CheckCircle else Icons.Default.Extension,
                        contentDescription = null,
                        tint = if (uiState.isModuleHooked) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NPatch / LSPatch Injection Architecture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (uiState.isModuleHooked)
                                "HOOK ACTIVE (Target: ${uiState.hookedPackage ?: "Detected"})"
                            else
                                "STANDALONE / READY FOR INJECTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isModuleHooked) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Generic Target Matching Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wildcard Camera Interception",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Automatically inject into any patched app requesting Camera2, CameraX, or Legacy Camera APIs.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isWildcardMode,
                        onCheckedChange = { viewModel.toggleWildcardMode() }
                    )
                }
            }
        }

        // Supported Target Applications List
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CONFIGURED TARGET APPLICATIONS (${uiState.supportedTargets.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                uiState.supportedTargets.forEach { target ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = target.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = target.preferredApi.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = target.packageName,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Add custom target input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPkgInput,
                        onValueChange = { newPkgInput = it },
                        placeholder = { Text("package.name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(
                        onClick = {
                            if (newPkgInput.isNotBlank()) {
                                viewModel.addTarget(
                                    TargetAppConfig(
                                        packageName = newPkgInput.trim(),
                                        displayName = newPkgInput.trim().substringAfterLast("."),
                                        description = "Custom user target"
                                    )
                                )
                                newPkgInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Target")
                    }
                }
            }
        }

        // NPatch Setup Guide
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "NPATCH / LSPATCH INJECTION STEPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                val instructions = listOf(
                    "1. Install KMJS APK on device.",
                    "2. Open NPatch or LSPatch.",
                    "3. Select target application to patch (or any camera app).",
                    "4. Embed module com.kmjs.virtualcamera.",
                    "5. Install the generated patched APK.",
                    "6. In KMJS, press CONNECT to start RTSP streaming.",
                    "7. Open the target app: physical camera frames are replaced with RTSP video!"
                )

                instructions.forEach { step ->
                    Text(
                        text = step,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AssistChip(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("com.kmjs.virtualcamera"))
                        },
                        label = { Text("Copy Package ID: com.kmjs.virtualcamera", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun LogsTab(
    logs: List<KmjsLog.LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var filterTag by remember { mutableStateOf<String?>(null) }

    val tags = listOf(
        null to "ALL",
        KmjsLog.TAG_MODULE to "MODULE",
        KmjsLog.TAG_PROCESS to "PROCESS",
        KmjsLog.TAG_TARGET to "TARGET",
        KmjsLog.TAG_INJECT to "INJECT",
        KmjsLog.TAG_CAMERA to "CAMERA",
        KmjsLog.TAG_RTSP to "RTSP",
        KmjsLog.TAG_FRAME to "FRAME",
        KmjsLog.TAG_SERVICE to "SERVICE",
        KmjsLog.TAG_ERROR to "ERROR"
    )

    val filteredLogs = if (filterTag == null) logs else logs.filter { it.tag == filterTag }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Tag Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tags.forEach { (tag, label) ->
                FilterChip(
                    selected = filterTag == tag,
                    onClick = { filterTag = tag },
                    label = { Text(label, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log Console
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0D1117),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No diagnostic logs yet",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { entry ->
                        val color = when (entry.level) {
                            "E" -> Color(0xFFFF5252)
                            "W" -> Color(0xFFFFB300)
                            "I" -> Color(0xFF00E676)
                            else -> Color(0xFF81D4FA)
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${entry.formattedTime} [${entry.tag}] ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = entry.message,
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
