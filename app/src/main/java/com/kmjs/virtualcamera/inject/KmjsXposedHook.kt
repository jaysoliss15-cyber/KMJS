package com.kmjs.virtualcamera.inject

import android.app.Application
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
 */
class KmjsXposedHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        var isModuleLoaded = false
        var currentHookedPackage: String? = null
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        isModuleLoaded = true
        KmjsLog.i(KmjsLog.TAG_INJECT, "KMJS Zygote initialized for NPatch/LSPatch")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        currentHookedPackage = packageName
        isModuleLoaded = true

        KmjsLog.i(KmjsLog.TAG_INJECT, "KMJS injection module loaded in target process: $packageName (Process: ${lpparam.processName})")

        // Hook Application.attachBaseContext to obtain target App Context
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
                            onTargetApplicationAttached(appContext, lpparam)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook attachBaseContext: ${t.message}")
        }

        // Install camera hooks
        installHooks(lpparam.classLoader, null)
    }

    private fun onTargetApplicationAttached(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        KmjsLog.i(KmjsLog.TAG_INJECT, "Target context attached for package: ${lpparam.packageName}")

        // Send heartbeat to KMJS Foreground Service
        val ipcClient = KmjsIpcClient(context)
        ipcClient.sendHeartbeat(lpparam.packageName)

        // Install / refresh hooks with context
        installHooks(lpparam.classLoader, context)
    }

    private fun installHooks(classLoader: ClassLoader?, context: Context?) {
        try {
            val camera2Hook = Camera2Hook(classLoader, context)
            val c2Success = camera2Hook.install()

            val legacyHook = LegacyCameraHook(classLoader)
            val legacySuccess = legacyHook.install()

            if (c2Success || legacySuccess) {
                KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installation SUCCESS for target package: $currentHookedPackage")
            } else {
                KmjsLog.w(KmjsLog.TAG_INJECT, "Hook installation completed with no active targets matched")
            }
        } catch (t: Throwable) {
            KmjsLog.e(KmjsLog.TAG_INJECT, "Hook installation FAILED: ${t.message}", t)
        }
    }
}
