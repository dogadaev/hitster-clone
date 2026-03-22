package com.hitster.platform.android

/**
 * Android-specific wake-lock bridge used to keep the host screen awake during active play.
 */

import android.view.Window
import android.view.WindowManager

class AndroidKeepScreenAwakeController {
    fun enable(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun disable(window: Window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
