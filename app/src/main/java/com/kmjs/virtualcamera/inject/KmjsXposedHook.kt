package com.kmjs.virtualcamera.inject

import android.content.Context
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Main NPatch / LSPatch / Xposed module entry point for KMJS Virtual Camera.
 * Referenced by /assets/xposed_init.
 *
 * Implements generic target detection, API discovery, and multi-camera hook dispatching.
 */
class KmjsXposedHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        var isModuleLoaded = false
        var currentHookedPackage: String? = null
        var currentHookedProcess: String? = null
        var activeHooks: List<CameraHook> = emptyList()
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        isModuleLoaded = true
        KmjsLog.event(
            KmjsLog.TAG_MODULE,
            "MODULE_START",
            "KMJS Zygote initialized for NPatch/LSPatch/Xposed"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val processName = lpparam.processName

        // 1. Process and Target Inspection
        val inspection = TargetProcessDetector.inspect(packageName, processName)
        if (!inspection.shouldInject) {
            KmjsLog.d(
                KmjsLog.TAG_PROCESS,
                "Skipping injection for $processName (${inspection.skipReason ?: "Not eligible"})"
            )
            return
        }

        isModuleLoaded = true
        currentHookedPackage = packageName
        currentHookedProcess = processName

        KmjsLog.event(
            KmjsLog.TAG_INJECT,
            "INJECT_TARGET_MATCHED",
            "Target: ${inspection.targetConfig?.displayName ?: packageName} in process $processName"
        )

        // 2. Camera API detection
        val apiDetection = CameraApiDetector.detect(lpparam.classLoader)

        // 3. Hook Application.attachBaseContext to obtain target Context
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attachBaseContext",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val appContext = param.args.getOrNull(0) as? Context
                        if (appContext != null) {
                            onTargetApplicationAttached(appContext, lpparam, apiDetection)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook Application.attachBaseContext: ${t.message}")
        }

        // 4. Install initial hooks without context (for early camera opens)
        installCameraHooks(lpparam.classLoader, null, apiDetection)
    }

    private fun onTargetApplicationAttached(
        context: Context,
        lpparam: XC_LoadPackage.LoadPackageParam,
        apiDetection: CameraApiDetectionResult
    ) {
        KmjsLog.event(
            KmjsLog.TAG_INJECT,
            "TARGET_CONTEXT_ATTACHED",
            "Package: ${lpparam.packageName}, Process: ${lpparam.processName}"
        )

        // Send heartbeat to KMJS Foreground Service via IPC
        try {
            val ipcClient = KmjsIpcClient(context)
            val ack = ipcClient.sendHeartbeat(lpparam.packageName)
            KmjsLog.i(KmjsLog.TAG_INJECT, "IPC Heartbeat to KMJS Service acknowledged: $ack")
        } catch (e: Exception) {
            KmjsLog.d(KmjsLog.TAG_INJECT, "Heartbeat attempt note: ${e.message}")
        }

        // Re-install/refresh camera hooks with valid application context
        installCameraHooks(lpparam.classLoader, context, apiDetection)
    }

    private fun installCameraHooks(
        classLoader: ClassLoader?,
        context: Context?,
        apiDetection: CameraApiDetectionResult
    ) {
        KmjsLog.event(KmjsLog.TAG_INJECT, "HOOK_INSTALL_START", "Target=$currentHookedPackage")

        val hooksToInstall = mutableListOf<CameraHook>()

        // Install hooks based on detected APIs or all applicable adapters
        val camera2Hook = Camera2Hook()
        hooksToInstall.add(camera2Hook)

        if (apiDetection.hasCameraX) {
            hooksToInstall.add(CameraXHook())
        }

        if (apiDetection.hasLegacyCamera) {
            hooksToInstall.add(LegacyCameraHook())
        }

        var anySuccess = false
        for (hook in hooksToInstall) {
            try {
                val success = hook.install(classLoader, context)
                if (success) {
                    anySuccess = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Adapter installed: ${hook.name} (${hook.apiType})")
                }
            } catch (t: Throwable) {
                KmjsLog.w(KmjsLog.TAG_INJECT, "Error installing ${hook.name}: ${t.message}")
            }
        }

        activeHooks = hooksToInstall

        if (anySuccess) {
            KmjsLog.event(
                KmjsLog.TAG_INJECT,
                "HOOK_INSTALL_SUCCESS",
                "Installed ${hooksToInstall.size} camera adapters for $currentHookedPackage"
            )
        } else {
            KmjsLog.event(
                KmjsLog.TAG_INJECT,
                "HOOK_INSTALL_FAILED",
                "No camera hooks could be registered for $currentHookedPackage"
            )
        }
    }
}
