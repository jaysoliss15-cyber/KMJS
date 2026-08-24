package com.kmjs.virtualcamera.inject

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.view.Surface
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Camera2 API Interceptor.
 * Hooks into CameraManager, CameraDevice capture sessions, and OutputConfigurations
 * to replace camera preview output with the KMJS RTSP virtual video stream.
 */
class Camera2Hook : CameraHook {

    override val name: String = "Camera2 Interceptor"
    override val apiType: CameraApiType = CameraApiType.CAMERA2

    private val activeRenderers = ConcurrentHashMap<Surface, VirtualCameraRenderer>()
    private var ipcClient: KmjsIpcClient? = null

    override fun install(classLoader: ClassLoader?, context: Context?): Boolean {
        if (context != null) {
            ipcClient = KmjsIpcClient(context)
        }

        var anyHooked = false

        try {
            // 1. Hook CameraManager.openCamera
            val cameraManagerClass = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CameraManager",
                classLoader
            )

            if (cameraManagerClass != null) {
                // Hook all overloads of openCamera
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraManagerClass,
                        "openCamera",
                        String::class.java,
                        CameraDevice.StateCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val cameraId = param.args.getOrNull(0) as? String ?: "0"
                                KmjsLog.event(
                                    KmjsLog.TAG_CAMERA,
                                    "CAMERA_OPEN",
                                    "CameraManager.openCamera(cameraId=$cameraId, callback, handler)"
                                )
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraManager.openCamera(String, StateCallback, Handler)")
                } catch (t: Throwable) {
                    KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook CameraManager.openCamera standard overload: ${t.message}")
                }

                // API 28+ overload with Executor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val executorClass = XposedHelpers.findClassIfExists("java.util.concurrent.Executor", classLoader)
                            ?: java.util.concurrent.Executor::class.java
                        XposedHelpers.findAndHookMethod(
                            cameraManagerClass,
                            "openCamera",
                            String::class.java,
                            executorClass,
                            CameraDevice.StateCallback::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val cameraId = param.args.getOrNull(0) as? String ?: "0"
                                    KmjsLog.event(
                                        KmjsLog.TAG_CAMERA,
                                        "CAMERA_OPEN",
                                        "CameraManager.openCamera(cameraId=$cameraId, executor, callback)"
                                    )
                                }
                            }
                        )
                        anyHooked = true
                        KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraManager.openCamera(String, Executor, StateCallback)")
                    } catch (t: Throwable) {
                        KmjsLog.d(KmjsLog.TAG_INJECT, "Executor openCamera overload note: ${t.message}")
                    }
                }
            }

            // 2. Hook CameraDevice and CameraDeviceImpl
            val deviceClasses = listOfNotNull(
                XposedHelpers.findClassIfExists("android.hardware.camera2.CameraDevice", classLoader),
                XposedHelpers.findClassIfExists("android.hardware.camera2.impl.CameraDeviceImpl", classLoader)
            )

            for (deviceClass in deviceClasses) {
                // Hook createCaptureSession(List<Surface>, ...)
                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "createCaptureSession",
                        List::class.java,
                        CameraCaptureSession.StateCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                @Suppress("UNCHECKED_CAST")
                                val surfaces = param.args.getOrNull(0) as? List<Surface>
                                onCaptureSessionOutputsRequested(surfaces)
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: ${deviceClass.simpleName}.createCaptureSession(List<Surface>)")
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "createCaptureSession(List) hook note on ${deviceClass.name}: ${t.message}")
                }

                // Hook createCaptureSessionByOutputConfigurations on API 24+
                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "createCaptureSessionByOutputConfigurations",
                        List::class.java,
                        CameraCaptureSession.StateCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val configs = param.args.getOrNull(0) as? List<*>
                                extractAndInterceptOutputConfigs(configs)
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: ${deviceClass.simpleName}.createCaptureSessionByOutputConfigurations")
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_INJECT, "createCaptureSessionByOutputConfigurations note: ${t.message}")
                }

                // Hook createCaptureSession(SessionConfiguration) on API 28+ (Android P)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val sessionConfigClass = XposedHelpers.findClassIfExists(
                            "android.hardware.camera2.params.SessionConfiguration",
                            classLoader
                        )
                        if (sessionConfigClass != null) {
                            XposedHelpers.findAndHookMethod(
                                deviceClass,
                                "createCaptureSession",
                                sessionConfigClass,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        val sessionConfig = param.args.getOrNull(0)
                                        if (sessionConfig != null) {
                                            extractAndInterceptSessionConfiguration(sessionConfig)
                                        }
                                    }
                                }
                            )
                            anyHooked = true
                            KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: ${deviceClass.simpleName}.createCaptureSession(SessionConfiguration)")
                        }
                    } catch (t: Throwable) {
                        KmjsLog.d(KmjsLog.TAG_INJECT, "SessionConfiguration hook note: ${t.message}")
                    }
                }
            }

            // 3. Hook CaptureRequest.Builder.addTarget(Surface)
            val captureRequestBuilderClass = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CaptureRequest\$Builder",
                classLoader
            )
            if (captureRequestBuilderClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(
                        captureRequestBuilderClass,
                        "addTarget",
                        Surface::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val surface = param.args.getOrNull(0) as? Surface
                                if (surface != null && surface.isValid) {
                                    interceptSurface(surface, "CaptureRequestTarget")
                                }
                            }
                        }
                    )
                    anyHooked = true
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CaptureRequest.Builder.addTarget(Surface)")
                } catch (t: Throwable) {
                    KmjsLog.w(KmjsLog.TAG_INJECT, "CaptureRequest.Builder.addTarget note: ${t.message}")
                }
            }

        } catch (t: Throwable) {
            KmjsLog.e(KmjsLog.TAG_INJECT, "Camera2Hook installation error: ${t.message}", t)
        }

        return anyHooked
    }

    private fun onCaptureSessionOutputsRequested(surfaces: List<Surface>?) {
        if (surfaces.isNullOrEmpty()) return
        KmjsLog.event(
            KmjsLog.TAG_CAMERA,
            "CAPTURE_SESSION_START",
            "Found ${surfaces.size} output surfaces"
        )

        for ((index, surface) in surfaces.withIndex()) {
            if (surface.isValid) {
                interceptSurface(surface, "SessionSurface_$index")
            }
        }
    }

    private fun extractAndInterceptOutputConfigs(outputConfigs: List<*>?) {
        if (outputConfigs.isNullOrEmpty()) return
        for ((index, outputConfig) in outputConfigs.withIndex()) {
            if (outputConfig != null) {
                try {
                    val surface = XposedHelpers.callMethod(outputConfig, "getSurface") as? Surface
                    if (surface != null && surface.isValid) {
                        interceptSurface(surface, "OutputConfigSurface_$index")
                    }
                } catch (t: Throwable) {
                    KmjsLog.d(KmjsLog.TAG_CAMERA, "Could not extract surface from OutputConfiguration: ${t.message}")
                }
            }
        }
    }

    private fun extractAndInterceptSessionConfiguration(sessionConfig: Any) {
        try {
            val outputConfigs = XposedHelpers.callMethod(sessionConfig, "getOutputConfigurations") as? List<*>
            if (outputConfigs != null) {
                KmjsLog.event(
                    KmjsLog.TAG_CAMERA,
                    "SESSION_CONFIG_DETECTED",
                    "${outputConfigs.size} OutputConfigurations"
                )
                extractAndInterceptOutputConfigs(outputConfigs)
            }
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_CAMERA, "Error extracting surfaces from SessionConfiguration: ${t.message}")
        }
    }

    private fun interceptSurface(surface: Surface, label: String) {
        if (!surface.isValid) {
            KmjsLog.d(KmjsLog.TAG_CAMERA, "Skipping invalid surface interception: $label")
            return
        }

        // Clean up any stale/terminated renderers
        val iterator = activeRenderers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.key.isValid || !entry.value.isAlive) {
                entry.value.stopRenderer()
                iterator.remove()
            }
        }

        val existing = activeRenderers[surface]
        if (existing != null && existing.isAlive) {
            return
        }

        KmjsLog.event(
            KmjsLog.TAG_CAMERA,
            "SURFACE_INTERCEPTED",
            "Camera2 output surface: $label (id=${System.identityHashCode(surface)})"
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
