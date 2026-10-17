package com.karmakameleon.shared.util

import kotlin.math.pow

/**
 * Parses a Reddit flair color such as "#ff4500" into an opaque ARGB int. Returns null when the
 * flair has no usable color, which Reddit sends as null, "", or "transparent".
 */
fun parseFlairColor(color: String?): Int? {
    val hex = color?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6) return null
    val rgb = hex.toIntOrNull(16) ?: return null
    return (0xFF shl 24) or rgb
}

/** Whether black text is more readable than white on [argb], by WCAG contrast ratio. */
fun flairNeedsDarkText(argb: Int): Boolean {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    val contrastWithBlack = (luminance + 0.05) / 0.05
    val contrastWithWhite = 1.05 / (luminance + 0.05)
    return contrastWithBlack >= contrastWithWhite
}
