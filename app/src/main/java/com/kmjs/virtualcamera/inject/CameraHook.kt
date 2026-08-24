package com.kmjs.virtualcamera.inject

import android.content.Context

/**
 * Common interface for camera API hooking and frame redirection adapters.
 */
interface CameraHook {
    val name: String
    val apiType: CameraApiType

    /**
     * Installs method hooks for this camera API.
     * @return true if at least one hook was successfully registered.
     */
    fun install(classLoader: ClassLoader?, context: Context?): Boolean

    /**
     * Releases active surface renderers and unregisters hooks if applicable.
     */
    fun releaseAll()
}
