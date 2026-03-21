package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator

internal fun createUiFont(size: Int): BitmapFont {
    val fontFile = Gdx.files.internal(UI_FONT_ASSET_PATH)
    if (!fontFile.exists()) {
        return BitmapFont().prepareUiFont()
    }

    val generator = FreeTypeFontGenerator(fontFile)
    return try {
        generator.generateFont(
            FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = size
                borderWidth = 1.1f
                characters = UI_FONT_CHARACTERS
                kerning = true
                hinting = FreeTypeFontGenerator.Hinting.Full
                magFilter = Texture.TextureFilter.Linear
                minFilter = Texture.TextureFilter.Linear
            },
        ).prepareUiFont()
    } finally {
        generator.dispose()
    }
}
