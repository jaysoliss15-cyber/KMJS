package com.kmjs.virtualcamera.inject

import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.XposedHelpers

/**
 * Inspection result detailing which Camera APIs are available in the target ClassLoader.
 */
data class CameraApiDetectionResult(
    val hasCamera2: Boolean,
    val hasCameraX: Boolean,
    val hasLegacyCamera: Boolean,
    val primaryDetectedApi: CameraApiType,
    val allDetectedApis: List<CameraApiType>
)

/**
 * Camera API Detection Layer.
 * Probes the target application's ClassLoader to determine supported camera subsystems.
 */
object CameraApiDetector {

    fun detect(classLoader: ClassLoader?): CameraApiDetectionResult {
        val hasCamera2 = probeClass("android.hardware.camera2.CameraManager", classLoader) != null
        val hasCameraX = probeClass("androidx.camera.core.CameraX", classLoader) != null ||
                probeClass("androidx.camera.lifecycle.ProcessCameraProvider", classLoader) != null ||
                probeClass("androidx.camera.core.Preview", classLoader) != null
        val hasLegacy = probeClass("android.hardware.Camera", classLoader) != null

        val detectedList = mutableListOf<CameraApiType>()
        if (hasCameraX) detectedList.add(CameraApiType.CAMERAX)
        if (hasCamera2) detectedList.add(CameraApiType.CAMERA2)
        if (hasLegacy) detectedList.add(CameraApiType.LEGACY)

        val primary = when {
            hasCameraX -> CameraApiType.CAMERAX
            hasCamera2 -> CameraApiType.CAMERA2
            hasLegacy -> CameraApiType.LEGACY
            else -> CameraApiType.CAMERA2 // default fallback
        }

        if (detectedList.isNotEmpty()) {
            KmjsLog.event(
                KmjsLog.TAG_CAMERA,
                "CAMERA_API_DETECTED",
                "Primary=$primary, Available=$detectedList"
            )
        } else {
            KmjsLog.event(
                KmjsLog.TAG_CAMERA,
                "CAMERA_API_UNSUPPORTED",
                "No standard camera classes found in ClassLoader, using standard Camera2 fallback"
            )
        }

        return CameraApiDetectionResult(
            hasCamera2 = hasCamera2,
            hasCameraX = hasCameraX,
            hasLegacyCamera = hasLegacy,
            primaryDetectedApi = primary,
            allDetectedApis = detectedList
        )
    }

    private fun probeClass(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            XposedHelpers.findClassIfExists(className, classLoader)
        } catch (t: Throwable) {
            null
        }
    }
}
