package com.kmjs.virtualcamera.inject

import android.graphics.SurfaceTexture
import android.view.SurfaceHolder
import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Legacy Android Camera 1 API Interceptor.
 * Hooks android.hardware.Camera preview surfaces and callbacks.
 */
class LegacyCameraHook(private val classLoader: ClassLoader?) {

    fun install(): Boolean {
        var anyHooked = false
        try {
            val cameraClass = XposedHelpers.findClassIfExists("android.hardware.Camera", classLoader)
            if (cameraClass != null) {
                // Hook Camera.open(int)
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "open",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val id = param.args.getOrNull(0) ?: 0
                            KmjsLog.i(KmjsLog.TAG_CAMERA, "Legacy Camera.open(cameraId=$id) called by target app")
                        }
                    }
                )

                // Hook Camera.setPreviewDisplay(SurfaceHolder)
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "setPreviewDisplay",
                    SurfaceHolder::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val holder = param.args.getOrNull(0) as? SurfaceHolder
                            if (holder != null && holder.surface.isValid) {
                                KmjsLog.i(KmjsLog.TAG_CAMERA, "Legacy Camera preview SurfaceHolder detected")
                                val renderer = VirtualCameraRenderer(holder.surface, surfaceName = "LegacySurfaceHolder")
                                renderer.start()
                            }
                        }
                    }
                )

                // Hook Camera.setPreviewTexture(SurfaceTexture)
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "setPreviewTexture",
                    SurfaceTexture::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val surfaceTexture = param.args.getOrNull(0) as? SurfaceTexture
                            if (surfaceTexture != null) {
                                KmjsLog.i(KmjsLog.TAG_CAMERA, "Legacy Camera SurfaceTexture detected")
                            }
                        }
                    }
                )

                anyHooked = true
                KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: Legacy android.hardware.Camera API")
            }
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "LegacyCameraHook setup note: ${t.message}")
        }
        return anyHooked
    }
}
