package com.reader.android.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun hideSystemBars(window: Window, view: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    val controller = WindowCompat.getInsetsController(window, view)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

fun showSystemBars(window: Window, view: View) {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
}
