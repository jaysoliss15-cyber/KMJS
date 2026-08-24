package com.kmjs.virtualcamera.inject

/**
 * Camera API types supported by KMJS Virtual Camera.
 */
enum class CameraApiType {
    AUTO,
    CAMERA2,
    CAMERAX,
    LEGACY
}

/**
 * Configuration descriptor for a supported camera application target.
 */
data class TargetAppConfig(
    val packageName: String,
    val displayName: String,
    val preferredApi: CameraApiType = CameraApiType.AUTO,
    val description: String = "",
    val enabled: Boolean = true,
    val isWildcard: Boolean = false
)

/**
 * Central registry of supported camera target applications.
 * Supports known camera applications, social/messaging apps, and generic wildcard interception
 * for any application patched via NPatch / LSPatch / Xposed.
 */
object SupportedTargetRegistry {

    private val targetMap = mutableMapOf<String, TargetAppConfig>()

    @Volatile
    var isWildcardModeEnabled: Boolean = true

    init {
        // Register known and common camera target configurations
        register(
            TargetAppConfig(
                packageName = "com.kmjs.virtualcamera",
                displayName = "KMJS Internal Target Test",
                preferredApi = CameraApiType.AUTO,
                description = "KMJS built-in test camera activity and viewfinder",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "com.photo.android.camera",
                displayName = "Android Photo Camera",
                preferredApi = CameraApiType.AUTO,
                description = "Standard Photo Camera application",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "com.android.camera",
                displayName = "AOSP System Camera",
                preferredApi = CameraApiType.AUTO,
                description = "Default AOSP Camera app",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "com.google.android.GoogleCamera",
                displayName = "Google Camera (GCam)",
                preferredApi = CameraApiType.CAMERA2,
                description = "Google Camera HDR+ pipeline",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "com.whatsapp",
                displayName = "WhatsApp Messenger",
                preferredApi = CameraApiType.CAMERAX,
                description = "WhatsApp CameraX/Camera2 capture session",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "com.android.chrome",
                displayName = "Google Chrome WebRTC",
                preferredApi = CameraApiType.CAMERA2,
                description = "WebRTC Camera2 video capture stream",
                enabled = true
            )
        )
        register(
            TargetAppConfig(
                packageName = "org.telegram.messenger",
                displayName = "Telegram Messenger",
                preferredApi = CameraApiType.AUTO,
                description = "Telegram Camera2 video/photo capture",
                enabled = true
            )
        )
    }

    fun register(config: TargetAppConfig) {
        synchronized(targetMap) {
            targetMap[config.packageName] = config
        }
    }

    fun unregister(packageName: String) {
        synchronized(targetMap) {
            targetMap.remove(packageName)
        }
    }

    /**
     * Resolves matching target configuration for a given package name.
     */
    fun findMatchingTarget(packageName: String): TargetAppConfig? {
        synchronized(targetMap) {
            val exact = targetMap[packageName]
            if (exact != null && exact.enabled) {
                return exact
            }

            // Fall back to wildcard mode for generic camera apps
            if (isWildcardModeEnabled) {
                return TargetAppConfig(
                    packageName = packageName,
                    displayName = "Generic Camera App ($packageName)",
                    preferredApi = CameraApiType.AUTO,
                    description = "Dynamic wildcard target matching",
                    enabled = true,
                    isWildcard = true
                )
            }

            return null
        }
    }

    fun isTargetSupported(packageName: String): Boolean {
        return findMatchingTarget(packageName) != null
    }

    fun getAllTargets(): List<TargetAppConfig> {
        synchronized(targetMap) {
            return targetMap.values.toList()
        }
    }
}
