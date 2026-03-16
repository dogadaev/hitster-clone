package com.hitster.ui

import com.badlogic.gdx.Gdx

fun runOnGameThread(action: () -> Unit) {
    val app = Gdx.app
    if (app == null) {
        action()
        return
    }
    app.postRunnable(action)
}
