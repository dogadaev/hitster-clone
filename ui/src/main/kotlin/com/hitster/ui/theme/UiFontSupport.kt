package com.hitster.ui.theme

/**
 * Shared font constants and post-processing helpers, including the glyph ranges required by the bundled playlists.
 */

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator

internal const val UI_FONT_ASSET_PATH = "fonts/droid-sans-bold.ttf"

internal val UI_FONT_CHARACTERS: String = buildString {
    append(FreeTypeFontGenerator.DEFAULT_CHARS)
    for (codePoint in 0x0400..0x052F) {
        append(codePoint.toChar())
    }
    append('\u2116')
    append("«»…–—")
}

internal fun BitmapFont.prepareUiFont(): BitmapFont = apply {
    setUseIntegerPositions(true)
    regions.forEach { region ->
        region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
}
