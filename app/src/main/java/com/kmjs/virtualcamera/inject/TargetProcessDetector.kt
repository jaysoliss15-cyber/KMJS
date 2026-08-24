package com.kmjs.virtualcamera.inject

import com.kmjs.virtualcamera.util.KmjsLog

/**
 * Result of target process inspection.
 */
data class ProcessInspectionResult(
    val packageName: String,
    val processName: String,
    val isMainProcess: Boolean,
    val isCameraProcess: Boolean,
    val isAuxiliaryProcess: Boolean,
    val targetConfig: TargetAppConfig?,
    val shouldInject: Boolean,
    val skipReason: String? = null
)

/**
 * Generic Target Process Detection Layer.
 * Analyzes incoming processes loaded under Xposed / NPatch / LSPatch,
 * filters out auxiliary processes (push services, leakcanary, analytics, etc.),
 * and checks against SupportedTargetRegistry.
 */
object TargetProcessDetector {

    // Known auxiliary process suffixes to avoid injecting unnecessary background workers
    private val AUXILIARY_PROCESS_SUFFIXES = listOf(
        ":push",
        ":pushservice",
        ":leakcanary",
        ":analytics",
        ":crashpad",
        ":sandboxed_process",
        ":privileged_process",
        ":daemon",
        ":devtools",
        ":report",
        ":channel"
    )

    // Known camera process suffixes
    private val CAMERA_PROCESS_SUFFIXES = listOf(
        ":camera",
        ":cameraserver",
        ":preview",
        ":video",
        ":capture"
    )

    /**
     * Inspects a process loaded by Xposed/NPatch/LSPatch.
     */
    fun inspect(packageName: String, processName: String): ProcessInspectionResult {
        KmjsLog.event(
            KmjsLog.TAG_PROCESS,
            "PROCESS_DETECTED",
            "pkg=$packageName, proc=$processName"
        )

        val isMain = processName == packageName
        val isCameraProc = CAMERA_PROCESS_SUFFIXES.any { processName.endsWith(it, ignoreCase = true) }
        val isAuxiliary = AUXILIARY_PROCESS_SUFFIXES.any { processName.endsWith(it, ignoreCase = true) }

        if (isAuxiliary) {
            val reason = "Filtered auxiliary process: $processName"
            KmjsLog.i(KmjsLog.TAG_PROCESS, reason)
            return ProcessInspectionResult(
                packageName = packageName,
                processName = processName,
                isMainProcess = isMain,
                isCameraProcess = isCameraProc,
                isAuxiliaryProcess = true,
                targetConfig = null,
                shouldInject = false,
                skipReason = reason
            )
        }

        val targetConfig = SupportedTargetRegistry.findMatchingTarget(packageName)
        if (targetConfig == null) {
            val reason = "TARGET_UNSUPPORTED: Package $packageName not registered and wildcard disabled"
            KmjsLog.event(KmjsLog.TAG_TARGET, "TARGET_UNSUPPORTED", "pkg=$packageName")
            return ProcessInspectionResult(
                packageName = packageName,
                processName = processName,
                isMainProcess = isMain,
                isCameraProcess = isCameraProc,
                isAuxiliaryProcess = false,
                targetConfig = null,
                shouldInject = false,
                skipReason = reason
            )
        }

        KmjsLog.event(
            KmjsLog.TAG_TARGET,
            "TARGET_SUPPORTED",
            "pkg=$packageName, target='${targetConfig.displayName}', preferredApi=${targetConfig.preferredApi}"
        )

        return ProcessInspectionResult(
            packageName = packageName,
            processName = processName,
            isMainProcess = isMain,
            isCameraProcess = isCameraProc,
            isAuxiliaryProcess = false,
            targetConfig = targetConfig,
            shouldInject = true
        )
    }
}
