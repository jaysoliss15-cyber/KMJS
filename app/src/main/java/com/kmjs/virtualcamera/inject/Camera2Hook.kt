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
 * Hooks into CameraManager and CameraDevice capture sessions to replace camera preview output
 * with the KMJS RTSP video stream.
 */
class Camera2Hook(
    private val classLoader: ClassLoader?,
    private val context: Context? = null
) {
    private val activeRenderers = ConcurrentHashMap<Surface, VirtualCameraRenderer>()
    private var ipcClient: KmjsIpcClient? = context?.let { KmjsIpcClient(it) }

    fun install(): Boolean {
        var anyHooked = false

        try {
            // 1. Hook CameraManager.openCamera
            val cameraManagerClass = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CameraManager",
                classLoader
            )

            if (cameraManagerClass != null) {
                XposedHelpers.findAndHookMethod(
                    cameraManagerClass,
                    "openCamera",
                    String::class.java,
                    CameraDevice.StateCallback::class.java,
                    Handler::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val cameraId = param.args.getOrNull(0) as? String ?: "0"
                            KmjsLog.i(KmjsLog.TAG_CAMERA, "Target app requesting CameraManager.openCamera (Camera ID: $cameraId)")
                        }
                    }
                )
                anyHooked = true
                KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraManager.openCamera")
            }

            // 2. Hook CameraDevice.createCaptureSession (List<Surface>, StateCallback, Handler)
            val cameraDeviceClass = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CameraDevice",
                classLoader
            ) ?: XposedHelpers.findClassIfExists(
                "android.hardware.camera2.impl.CameraDeviceImpl",
                classLoader
            )

            if (cameraDeviceClass != null) {
                // Hook createCaptureSession(List<Surface>, ...)
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraDeviceClass,
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
                    KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraDevice.createCaptureSession(List<Surface>)")
                } catch (t: Throwable) {
                    KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook CameraDevice.createCaptureSession list variant: ${t.message}")
                }

                // 3. Hook createCaptureSession(SessionConfiguration) on Android P (API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val sessionConfigClass = XposedHelpers.findClassIfExists(
                            "android.hardware.camera2.params.SessionConfiguration",
                            classLoader
                        )
                        if (sessionConfigClass != null) {
                            XposedHelpers.findAndHookMethod(
                                cameraDeviceClass,
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
                            KmjsLog.i(KmjsLog.TAG_INJECT, "Hook installed: CameraDevice.createCaptureSession(SessionConfiguration)")
                        }
                    } catch (t: Throwable) {
                        KmjsLog.w(KmjsLog.TAG_INJECT, "Could not hook SessionConfiguration: ${t.message}")
                    }
                }
            }

            // 4. Hook CaptureRequest.Builder.addTarget(Surface)
            val captureRequestBuilderClass = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CaptureRequest\$Builder",
                classLoader
            )
            if (captureRequestBuilderClass != null) {
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
            }

        } catch (t: Throwable) {
            KmjsLog.e(KmjsLog.TAG_INJECT, "Camera2Hook installation failed: ${t.message}", t)
        }

        return anyHooked
    }

    private fun onCaptureSessionOutputsRequested(surfaces: List<Surface>?) {
        if (surfaces.isNullOrEmpty()) return
        KmjsLog.i(KmjsLog.TAG_CAMERA, "Camera session detected with ${surfaces.size} output surfaces")

        for ((index, surface) in surfaces.withIndex()) {
            if (surface.isValid) {
                interceptSurface(surface, "SessionSurface_$index")
            }
        }
    }

    private fun extractAndInterceptSessionConfiguration(sessionConfig: Any) {
        try {
            val outputConfigs = XposedHelpers.callMethod(sessionConfig, "getOutputConfigurations") as? List<*>
            if (outputConfigs != null) {
                KmjsLog.i(KmjsLog.TAG_CAMERA, "Camera session detected via SessionConfiguration (${outputConfigs.size} outputs)")
                for ((index, outputConfig) in outputConfigs.withIndex()) {
                    if (outputConfig != null) {
                        val surface = XposedHelpers.callMethod(outputConfig, "getSurface") as? Surface
                        if (surface != null && surface.isValid) {
                            interceptSurface(surface, "ConfigSurface_$index")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            KmjsLog.w(KmjsLog.TAG_CAMERA, "Error extracting surfaces from SessionConfiguration: ${t.message}")
        }
    }

    private fun interceptSurface(surface: Surface, label: String) {
        if (activeRenderers.containsKey(surface)) return

        KmjsLog.i(KmjsLog.TAG_CAMERA, "Intercepting camera output surface: $label")
        val renderer = VirtualCameraRenderer(
            targetSurface = surface,
            ipcClient = ipcClient,
            surfaceName = label
        )
        activeRenderers[surface] = renderer
        renderer.start()
    }

    fun releaseAll() {
        for ((_, renderer) in activeRenderers) {
            renderer.stopRenderer()
        }
        activeRenderers.clear()
    }
}
