package com.karmakameleon.shared.util

import kotlin.math.pow

private const val HEX_COLOR_LENGTH = 6
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF shl 24

// sRGB transfer function constants, for converting a channel to linear light.
private const val SRGB_LINEAR_THRESHOLD = 0.04045
private const val SRGB_LINEAR_SLOPE = 12.92
private const val SRGB_GAMMA_OFFSET = 0.055
private const val SRGB_GAMMA_SCALE = 1.055
private const val SRGB_GAMMA = 2.4

/**
 * Parses a Reddit flair color such as "#ff4500" into an opaque ARGB int. Returns null when the
 * flair has no usable color, which Reddit sends as null, "", or "transparent".
 */
fun parseFlairColor(color: String?): Int? {
    val hex = color?.trim()?.removePrefix("#") ?: return null
    if (hex.length != HEX_COLOR_LENGTH) return null
    val rgb = hex.toIntOrNull(HEX_RADIX) ?: return null
    return OPAQUE_ALPHA or rgb
}

/** Whether black text is more readable than white on [argb], by WCAG contrast ratio. */
fun flairNeedsDarkText(argb: Int): Boolean {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= SRGB_LINEAR_THRESHOLD) c / SRGB_LINEAR_SLOPE else ((c + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_SCALE).pow(SRGB_GAMMA)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    val contrastWithBlack = (luminance + 0.05) / 0.05
    val contrastWithWhite = 1.05 / (luminance + 0.05)
    return contrastWithBlack >= contrastWithWhite
}
