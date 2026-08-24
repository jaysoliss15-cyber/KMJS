package com.kmjs.virtualcamera.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kmjs.virtualcamera.frame.FrameStats
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.inject.KmjsXposedHook
import com.kmjs.virtualcamera.inject.SupportedTargetRegistry
import com.kmjs.virtualcamera.inject.TargetAppConfig
import com.kmjs.virtualcamera.rtsp.KmjsRtspPipeline
import com.kmjs.virtualcamera.rtsp.RtspConnectionState
import com.kmjs.virtualcamera.service.KmjsRtspService
import com.kmjs.virtualcamera.util.KmjsLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KmjsUiState(
    val rtspUrl: String = "rtsp://9627b0bf2a7b.entrypoint.cloud.wowza.com:1935/app-p5260J38/66abe4b9_stream1",
    val connectionState: RtspConnectionState = RtspConnectionState.DISCONNECTED,
    val statusMessage: String = "Disconnected",
    val frameStats: FrameStats = FrameStats(),
    val isServiceRunning: Boolean = false,
    val isTestPatternActive: Boolean = false,
    val isModuleHooked: Boolean = false,
    val hookedPackage: String? = null,
    val hookedProcess: String? = null,
    val isWildcardMode: Boolean = true,
    val supportedTargets: List<TargetAppConfig> = emptyList(),
    val selectedTab: Int = 0 // 0: Stream & Preview, 1: Camera Test, 2: NPatch / LSPatch & Targets, 3: Logs
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _uiState = MutableStateFlow(KmjsUiState())
    val uiState: StateFlow<KmjsUiState> = _uiState.asStateFlow()

    val logEntries: StateFlow<List<KmjsLog.LogEntry>> = KmjsLog.logsFlow

    private var boundService: KmjsRtspService? = null
    private var boundPipeline: KmjsRtspPipeline? = null

    val presetUrls = listOf(
        "rtsp://9627b0bf2a7b.entrypoint.cloud.wowza.com:1935/app-p5260J38/66abe4b9_stream1",
        "rtsp://192.168.1.38:8554/live/obs",
        "rtsp://192.168.1.100:8554/live",
        "rtsp://10.0.2.2:8554/live"
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val localBinder = service as? KmjsRtspService.LocalBinder
            boundService = localBinder?.getService()
            boundPipeline = localBinder?.pipeline

            KmjsLog.i(KmjsLog.TAG_SERVICE, "Activity bound to KmjsRtspService")
            observeBoundPipeline()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            KmjsLog.i(KmjsLog.TAG_SERVICE, "Activity unbound from KmjsRtspService")
            boundService = null
            boundPipeline = null
        }
    }

    init {
        KmjsLog.event(KmjsLog.TAG_GENERAL, "KMJS_APP_START", "MainViewModel initialized")

        // Observe FrameManager statistics
        viewModelScope.launch {
            KmjsFrameManager.statsFlow.collect { stats ->
                _uiState.value = _uiState.value.copy(frameStats = stats)
            }
        }

        // Observe service running state
        viewModelScope.launch {
            KmjsRtspService.isServiceRunning.collect { isRunning ->
                _uiState.value = _uiState.value.copy(isServiceRunning = isRunning)
            }
        }

        refreshTargetsList()
        bindToRtspService()
    }

    fun refreshTargetsList() {
        _uiState.value = _uiState.value.copy(
            isModuleHooked = KmjsXposedHook.isModuleLoaded,
            hookedPackage = KmjsXposedHook.currentHookedPackage,
            hookedProcess = KmjsXposedHook.currentHookedProcess,
            isWildcardMode = SupportedTargetRegistry.isWildcardModeEnabled,
            supportedTargets = SupportedTargetRegistry.getAllTargets()
        )
    }

    fun setRtspUrl(url: String) {
        _uiState.value = _uiState.value.copy(rtspUrl = url)
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun toggleWildcardMode() {
        val next = !_uiState.value.isWildcardMode
        SupportedTargetRegistry.isWildcardModeEnabled = next
        _uiState.value = _uiState.value.copy(isWildcardMode = next)
        KmjsLog.i(KmjsLog.TAG_TARGET, "Wildcard target matching mode: $next")
    }

    fun addTarget(config: TargetAppConfig) {
        SupportedTargetRegistry.register(config)
        refreshTargetsList()
    }

    fun connect() {
        val url = _uiState.value.rtspUrl.trim()
        if (url.isEmpty()) return

        KmjsLog.event(KmjsLog.TAG_GENERAL, "CONNECT_CLICKED", "URL: $url")
        KmjsRtspService.start(context, url)
        bindToRtspService()
    }

    fun disconnect() {
        KmjsLog.event(KmjsLog.TAG_GENERAL, "DISCONNECT_CLICKED")
        KmjsRtspService.stop(context)
        KmjsFrameManager.stopTestPatternGenerator()
        _uiState.value = _uiState.value.copy(
            connectionState = RtspConnectionState.DISCONNECTED,
            statusMessage = "Disconnected",
            isTestPatternActive = false
        )
    }

    fun toggleTestPattern() {
        val nextState = !_uiState.value.isTestPatternActive
        _uiState.value = _uiState.value.copy(isTestPatternActive = nextState)

        if (nextState) {
            KmjsFrameManager.startTestPatternGenerator(30, "KMJS Test Pattern Active")
            _uiState.value = _uiState.value.copy(
                connectionState = RtspConnectionState.CONNECTED,
                statusMessage = "Generating SMPTE Test Stream (30 FPS)"
            )
        } else {
            KmjsFrameManager.stopTestPatternGenerator()
            _uiState.value = _uiState.value.copy(
                connectionState = RtspConnectionState.DISCONNECTED,
                statusMessage = "Disconnected"
            )
        }
    }

    fun attachPreviewSurface(surface: Surface) {
        boundPipeline?.attachPreviewSurface(surface)
    }

    fun detachPreviewSurface() {
        boundPipeline?.detachPreviewSurface()
    }

    fun clearLogs() {
        KmjsLog.clear()
    }

    private fun bindToRtspService() {
        val intent = Intent(context, KmjsRtspService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeBoundPipeline() {
        val pipeline = boundPipeline ?: return
        viewModelScope.launch {
            pipeline.stateFlow.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            pipeline.statusMessage.collect { msg ->
                _uiState.value = _uiState.value.copy(statusMessage = msg)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
