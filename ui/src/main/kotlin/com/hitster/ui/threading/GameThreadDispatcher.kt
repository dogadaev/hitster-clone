package com.hitster.ui.threading

/**
 * Utility for marshaling platform and transport callbacks onto the libGDX render thread.
 */

import com.badlogic.gdx.Gdx

fun runOnGameThread(action: () -> Unit) {
    val app = Gdx.app
    if (app == null) {
        action()
        return
    }
    app.postRunnable(action)
}
