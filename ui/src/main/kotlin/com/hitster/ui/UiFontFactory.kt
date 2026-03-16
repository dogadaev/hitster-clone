package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator

internal fun createUiFont(size: Int): BitmapFont {
    val fontFile = Gdx.files.internal(FONT_ASSET_PATH)
    if (!fontFile.exists()) {
        return BitmapFont()
    }

    val generator = FreeTypeFontGenerator(fontFile)
    return generator.generateFont(
        FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = size
            borderWidth = 1.1f
            magFilter = Texture.TextureFilter.Linear
            minFilter = Texture.TextureFilter.Linear
        },
    ).also {
        generator.dispose()
    }
}

private const val FONT_ASSET_PATH = "fonts/droid-sans-bold.ttf"
