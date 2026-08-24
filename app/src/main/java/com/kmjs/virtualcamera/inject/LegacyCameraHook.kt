package com.kmjs.virtualcamera.inject

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.SurfaceHolder
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/**
 * Legacy Android Camera 1 API Interceptor.
 * Hooks android.hardware.Camera preview surfaces and callbacks.
 */
class LegacyCameraHook : CameraHook {

    override val name: String = "Legacy Camera Interceptor"
    override val apiType: CameraApiType = CameraApiType.LEGACY

    private val activeRenderers = ConcurrentHashMap<Any, VirtualCameraRenderer>()
    private var ipcClient: KmjsIpcClient? = null

    override fun install(classLoader: ClassLoader?, context: Context?): Boolean {
        if (context != null) {
            ipcClient = KmjsIpcClient(context)
        }

        var anyHooked = false
        try {
            val cameraClass = XposedHelpers.findClassIfExists("android.hardware.Camera", classLoader)
            if (cameraClass != null) {
                // Hook Camera.open(int)
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraClass,
                        "open",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val id = param.args.getOrNull(0) ?: 0
                                KmjsLog.event(
                                    KmjsLog.TAG_CAMERA,
                                    "LEGACY_CAMERA_OPEN",
                                    "Camera.open(cameraId=$id)"
                                )
                            }
                        }
                    )
                    anyHooked = true
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "Legacy Camera.open hook note: ${t.message}")
                }

                // Hook Camera.setPreviewDisplay(SurfaceHolder)
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraClass,
                        "setPreviewDisplay",
                        SurfaceHolder::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val holder = param.args.getOrNull(0) as? SurfaceHolder
                                if (holder != null && holder.surface.isValid) {
                                    KmjsLog.event(
                                        KmjsLog.TAG_CAMERA,
                                        "SURFACE_INTERCEPTED",
                                        "Legacy Camera preview SurfaceHolder"
                                    )
                                    val renderer = VirtualCameraRenderer(
                                        targetSurface = holder.surface,
                                        ipcClient = ipcClient,
                                        surfaceName = "LegacySurfaceHolder"
                                    )
                                    activeRenderers[holder] = renderer
                                    renderer.start()
                                }
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: Legacy Camera.setPreviewDisplay")
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "Legacy Camera.setPreviewDisplay hook note: ${t.message}")
                }

                // Hook Camera.setPreviewTexture(SurfaceTexture)
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraClass,
                        "setPreviewTexture",
                        SurfaceTexture::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val surfaceTexture = param.args.getOrNull(0) as? SurfaceTexture
                                if (surfaceTexture != null) {
                                    KmjsLog.event(
                                        KmjsLog.TAG_CAMERA,
                                        "SURFACE_INTERCEPTED",
                                        "Legacy Camera SurfaceTexture"
                                    )
                                }
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: Legacy Camera.setPreviewTexture")
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "Legacy Camera.setPreviewTexture hook note: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "LegacyCameraHook setup note: ${t.message}")
        }
        return anyHooked
    }

    override fun releaseAll() {
        for ((_, renderer) in activeRenderers) {
            renderer.stopRenderer()
        }
        activeRenderers.clear()
    }
}
