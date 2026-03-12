package com.hitster.ui

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.hitster.animations.AnimationCatalog
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.playback.api.PlaybackController

class HitsterGameApp(
    playbackController: PlaybackController = NoOpPlaybackController(),
) : Game() {
    private val presenter = UiBootstrapper.createPresenter(playbackController)
    private val automatedGuestBot = UiBootstrapper.createAutomatedGuestBot(presenter)
    private val animationCatalog = AnimationCatalog.default()

    override fun create() {
        setScreen(MatchScreen(presenter, animationCatalog))
    }

    override fun render() {
        val deltaSeconds = Gdx.graphics.deltaTime
        super.render()
        automatedGuestBot?.update(deltaSeconds)
    }
}
