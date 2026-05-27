package com.contextauth.core

import android.content.res.Configuration
import android.view.Surface

object CoarseOrientation {
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
    const val PORTRAIT_REVERSE = "portrait_reverse"
    const val LANDSCAPE_REVERSE = "landscape_reverse"
    const val UNKNOWN = "unknown"

    fun fromAndroid(configurationOrientation: Int, rotation: Int?): String {
        val base = when (configurationOrientation) {
            Configuration.ORIENTATION_PORTRAIT -> PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
            else -> UNKNOWN
        }
        return when {
            base == PORTRAIT && rotation == Surface.ROTATION_180 -> PORTRAIT_REVERSE
            base == LANDSCAPE && rotation == Surface.ROTATION_270 -> LANDSCAPE_REVERSE
            else -> base
        }
    }

    fun normalize(value: String?): String = when (value) {
        PORTRAIT, LANDSCAPE, PORTRAIT_REVERSE, LANDSCAPE_REVERSE -> value
        else -> UNKNOWN
    }
}
