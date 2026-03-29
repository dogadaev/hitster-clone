package com.hitster.ui.render

/**
 * Shader-backed liquid-glass surface renderer used for panels and buttons.
 *
 * The shader does not attempt a full scene blur because the UI currently renders without a dedicated
 * composited background buffer, but it does add animated refraction-like highlights, rim lighting,
 * and soft internal glow so chrome reads as real glass rather than stacked translucent shapes.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Rectangle
import kotlin.math.max
import kotlin.math.min

internal data class LiquidGlassStyle(
    val bodyTint: Long,
    val edgeTint: Long,
    val highlightTint: Long,
    val glowTint: Long = highlightTint,
    val distortion: Float = 0.014f,
    val frost: Float = 0.18f,
)

internal class LiquidGlassSurfaceRenderer {
    private lateinit var surfaceTexture: Texture
    private lateinit var shader: ShaderProgram
    private val tempColor = Color()

    fun load() {
        if (this::shader.isInitialized) {
            return
        }
        surfaceTexture = createFlatTexture()
        ShaderProgram.pedantic = false
        shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        check(shader.isCompiled) { "Liquid glass shader compilation failed: ${shader.log}" }
    }

    fun draw(
        batch: SpriteBatch,
        rect: Rectangle,
        radius: Float,
        style: LiquidGlassStyle,
        timeSeconds: Float,
        pressed: Float = 0f,
    ) {
        if (!this::shader.isInitialized || rect.width <= 0f || rect.height <= 0f) {
            return
        }

        batch.flush()
        val previousShader = batch.shader
        batch.shader = shader
        shader.setUniformf("u_rectSize", rect.width, rect.height)
        shader.setUniformf("u_radius", min(radius, min(rect.width, rect.height) / 2f))
        shader.setUniformf("u_time", timeSeconds)
        shader.setUniformf("u_distortion", style.distortion)
        shader.setUniformf("u_frost", style.frost)
        shader.setUniformf("u_pressed", pressed.coerceIn(0f, 1f))
        setUniformColor("u_bodyTint", style.bodyTint)
        setUniformColor("u_edgeTint", style.edgeTint)
        setUniformColor("u_highlightTint", style.highlightTint)
        setUniformColor("u_glowTint", style.glowTint)
        batch.setColor(Color.WHITE)
        batch.draw(surfaceTexture, rect.x, rect.y, rect.width, rect.height)
        batch.flush()
        batch.shader = previousShader
    }

    fun dispose() {
        if (this::shader.isInitialized) {
            shader.dispose()
        }
        if (this::surfaceTexture.isInitialized) {
            surfaceTexture.dispose()
        }
    }

    private fun setUniformColor(name: String, rgba: Long) {
        shader.setUniformf(name, rgbaToColor(rgba))
    }

    private fun rgbaToColor(rgba: Long): Color {
        tempColor.set(
            (((rgba shr 24) and 0xFF) / 255f).toFloat(),
            (((rgba shr 16) and 0xFF) / 255f).toFloat(),
            (((rgba shr 8) and 0xFF) / 255f).toFloat(),
            ((rgba and 0xFF) / 255f).toFloat(),
        )
        return tempColor
    }

    private fun createFlatTexture(): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;

void main() {
    v_color = a_color;
    v_texCoords = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
"""

        const val FRAGMENT_SHADER = """
#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform vec2 u_rectSize;
uniform float u_radius;
uniform float u_time;
uniform vec4 u_bodyTint;
uniform vec4 u_edgeTint;
uniform vec4 u_highlightTint;
uniform vec4 u_glowTint;
uniform float u_distortion;
uniform float u_frost;
uniform float u_pressed;

float sdRoundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 size = max(u_rectSize, vec2(1.0));
    float minDim = max(min(size.x, size.y), 1.0);
    vec2 normalizedSize = size / minDim;
    vec2 p = (v_texCoords - 0.5) * normalizedSize;
    float radius = min(u_radius / minDim, min(normalizedSize.x, normalizedSize.y) * 0.5);
    vec2 halfSize = normalizedSize * 0.5;
    float dist = sdRoundedBox(p, halfSize, radius);
    float mask = 1.0 - smoothstep(0.0, 0.018, dist);
    if (mask <= 0.001) {
        discard;
    }

    float rim = 1.0 - smoothstep(0.0, 0.055, abs(dist));
    vec2 waveUv = v_texCoords * vec2(1.45, 1.75);
    float waveA = sin(waveUv.y * 12.0 + u_time * 0.80 + sin(waveUv.x * 3.20) * 0.80);
    float waveB = cos((waveUv.x * 1.40 - waveUv.y * 0.50) * 13.0 - u_time * 0.72);
    float waveC = sin((waveUv.x * 2.00 + waveUv.y * 1.60) * 8.50 + u_time * 1.15);
    float liquid = (waveA + waveB + waveC) / 3.0;
    vec2 offset = vec2(waveB, waveA) * u_distortion;
    vec2 shifted = clamp(v_texCoords + offset, 0.0, 1.0);
    float diagonal = shifted.y + shifted.x * 0.55;
    float diagonalSheen = smoothstep(0.16, 0.78, diagonal) * (1.0 - smoothstep(0.74, 1.08, diagonal));
    float hotspot = exp(-16.0 * distance(shifted, vec2(0.28 + waveC * 0.04, 0.78 + waveB * 0.03)));
    float centerGlow = 1.0 - smoothstep(0.08, 0.85, distance(v_texCoords, vec2(0.5)));
    float frostNoise = 0.5 + 0.5 * sin((shifted.x * 31.0 + shifted.y * 23.0 + u_time * 0.60) + liquid * 1.70);
    float pressedShade = mix(1.0, 0.86, u_pressed);

    vec3 color = u_bodyTint.rgb * (1.00 + liquid * 0.02) * pressedShade;
    color += u_edgeTint.rgb * rim * 0.24;
    color += u_highlightTint.rgb * (diagonalSheen * 0.10 + hotspot * 0.10 + centerGlow * 0.04);
    color += u_glowTint.rgb * frostNoise * u_frost * 0.08;

    float alpha = u_bodyTint.a * (0.96 + rim * 0.04) * mask;
    alpha += diagonalSheen * 0.01 * mask;
    alpha = clamp(alpha, 0.0, 1.0);

    gl_FragColor = vec4(color, alpha) * v_color;
}
"""
    }
}
