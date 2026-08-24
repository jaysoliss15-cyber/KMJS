package com.kmjs.virtualcamera.inject

import android.content.Context
import android.view.Surface
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * CameraX Jetpack API Interceptor.
 * Hooks into androidx.camera.core.SurfaceRequest and Preview components
 * to intercept preview surfaces configured by modern CameraX applications.
 */
class CameraXHook : CameraHook {

    override val name: String = "CameraX Interceptor"
    override val apiType: CameraApiType = CameraApiType.CAMERAX

    private val activeRenderers = ConcurrentHashMap<Surface, VirtualCameraRenderer>()
    private var ipcClient: KmjsIpcClient? = null

    override fun install(classLoader: ClassLoader?, context: Context?): Boolean {
        if (context != null) {
            ipcClient = KmjsIpcClient(context)
        }

        var anyHooked = false

        try {
            // 1. Hook SurfaceRequest.provideSurface(Surface, Executor, Consumer)
            val surfaceRequestClass = XposedHelpers.findClassIfExists(
                "androidx.camera.core.SurfaceRequest",
                classLoader
            )

            if (surfaceRequestClass != null) {
                // Hook provideSurface
                for (method in surfaceRequestClass.declaredMethods) {
                    if (method.name == "provideSurface" && method.parameterTypes.isNotEmpty()) {
                        if (method.parameterTypes[0] == Surface::class.java) {
                            try {
                                XposedHelpers.findAndHookMethod(
                                    surfaceRequestClass,
                                    "provideSurface",
                                    *method.parameterTypes,
                                    object : XC_MethodHook() {
                                        override fun beforeHookedMethod(param: MethodHookParam) {
                                            val surface = param.args.getOrNull(0) as? Surface
                                            if (surface != null && surface.isValid) {
                                                KmjsLog.i(KmjsLog.TAG_CAMERA, "CameraX SurfaceRequest.provideSurface intercepted")
                                                interceptSurface(surface, "CameraXSurfaceRequest")
                                            }
                                        }
                                    }
                                )
                                anyHooked = true
                                KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraX SurfaceRequest.provideSurface")
                                break
                            } catch (t: Throwable) {
                                KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook CameraX provideSurface: ${t.message}")
                            }
                        }
                    }
                }
            }

            // 2. Hook Preview.setSurfaceProvider
            val previewClass = XposedHelpers.findClassIfExists(
                "androidx.camera.core.Preview",
                classLoader
            )
            if (previewClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(
                        previewClass,
                        "setSurfaceProvider",
                        XposedHelpers.findClass("androidx.camera.core.Preview\$SurfaceProvider", classLoader),
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                KmjsLog.i(KmjsLog.TAG_CAMERA, "Target app configured CameraX Preview.setSurfaceProvider")
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraX Preview.setSurfaceProvider")
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "Preview.setSurfaceProvider note: ${t.message}")
                }
            }

        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "CameraXHook installation note: ${t.message}")
        }

        return anyHooked
    }

    private fun interceptSurface(surface: Surface, label: String) {
        if (activeRenderers.containsKey(surface)) return

        KmjsLog.event(
            KmjsLog.TAG_CAMERA,
            "SURFACE_INTERCEPTED",
            "CameraX output surface: $label"
        )
        val renderer = VirtualCameraRenderer(
            targetSurface = surface,
            ipcClient = ipcClient,
            surfaceName = label
        )
        activeRenderers[surface] = renderer
        renderer.start()
    }

    override fun releaseAll() {
        for ((_, renderer) in activeRenderers) {
            renderer.stopRenderer()
        }
        activeRenderers.clear()
    }
}
