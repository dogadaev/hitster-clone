package com.hitster.ui.render

/**
 * Shader-backed liquid-glass surface renderer used for panels and buttons.
 *
 * The renderer captures the already-drawn scene and then samples that texture inside the shader so
 * chrome can refract, smear, and split the light behind it instead of only tinting a flat fill.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.Texture.TextureWrap
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
    val topCornerRoundness: Float = 1f,
    val bottomCornerRoundness: Float = 1f,
)

internal class LiquidGlassSurfaceRenderer {
    private lateinit var surfaceTexture: Texture
    private var sceneTexture: Texture? = null
    private lateinit var shader: ShaderProgram
    private val tempColor = Color()
    private var sceneTextureWidth = 0
    private var sceneTextureHeight = 0
    private var sceneCaptureReady = false

    fun load() {
        if (this::shader.isInitialized) {
            return
        }
        surfaceTexture = createFlatTexture()
        ShaderProgram.pedantic = false
        shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        check(shader.isCompiled) { "Liquid glass shader compilation failed: ${shader.log}" }
    }

    fun captureBackbuffer() {
        if (!this::shader.isInitialized) {
            return
        }
        val width = Gdx.graphics.backBufferWidth.coerceAtLeast(1)
        val height = Gdx.graphics.backBufferHeight.coerceAtLeast(1)
        ensureSceneTexture(width, height)
        val capture = sceneTexture ?: return
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1)
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, capture.textureObjectHandle)
        Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        sceneCaptureReady = true
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
        shader.setUniformf("u_topCornerRoundness", style.topCornerRoundness.coerceIn(0f, 1f))
        shader.setUniformf("u_bottomCornerRoundness", style.bottomCornerRoundness.coerceIn(0f, 1f))
        shader.setUniformf(
            "u_screenSize",
            Gdx.graphics.backBufferWidth.coerceAtLeast(1).toFloat(),
            Gdx.graphics.backBufferHeight.coerceAtLeast(1).toFloat(),
        )
        shader.setUniformi("u_sceneTexture", 1)
        shader.setUniformf("u_hasSceneTexture", if (sceneCaptureReady && sceneTexture != null) 1f else 0f)
        setUniformColor("u_bodyTint", style.bodyTint)
        setUniformColor("u_edgeTint", style.edgeTint)
        setUniformColor("u_highlightTint", style.highlightTint)
        setUniformColor("u_glowTint", style.glowTint)
        sceneTexture?.takeIf { sceneCaptureReady }?.let { capture ->
            capture.bind(1)
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        }
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
        sceneTexture?.dispose()
        sceneTexture = null
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

    private fun ensureSceneTexture(width: Int, height: Int) {
        if (sceneTextureWidth == width && sceneTextureHeight == height && sceneTexture != null) {
            return
        }
        sceneTexture?.dispose()
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        pixmap.setColor(0f, 0f, 0f, 0f)
        pixmap.fill()
        sceneTexture = Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
            texture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge)
        }
        sceneTextureWidth = width
        sceneTextureHeight = height
        sceneCaptureReady = false
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
uniform vec2 u_screenSize;
uniform sampler2D u_sceneTexture;
uniform float u_hasSceneTexture;
uniform float u_topCornerRoundness;
uniform float u_bottomCornerRoundness;

float sdRoundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float sdBox(vec2 p, vec2 halfSize) {
    vec2 q = abs(p) - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}

float sdBottomRoundedBox(vec2 p, vec2 halfSize, float r) {
    float safeRadius = max(r, 0.0001);
    float upperHalfHeight = max(halfSize.y - safeRadius * 0.5, 0.0001);
    float bottomStripHalfWidth = max(halfSize.x - safeRadius, 0.0001);
    float bottomStripHalfHeight = max(safeRadius * 0.5, 0.0001);

    float mainRect = sdBox(
        p - vec2(0.0, safeRadius * 0.5),
        vec2(halfSize.x, upperHalfHeight)
    );
    float bottomStrip = sdBox(
        p - vec2(0.0, -halfSize.y + safeRadius * 0.5),
        vec2(bottomStripHalfWidth, bottomStripHalfHeight)
    );
    float leftCorner = length(p - vec2(-halfSize.x + safeRadius, -halfSize.y + safeRadius)) - safeRadius;
    float rightCorner = length(p - vec2(halfSize.x - safeRadius, -halfSize.y + safeRadius)) - safeRadius;
    return min(min(mainRect, bottomStrip), min(leftCorner, rightCorner));
}

void main() {
    vec2 size = max(u_rectSize, vec2(1.0));
    float minDim = max(min(size.x, size.y), 1.0);
    vec2 normalizedSize = size / minDim;
    vec2 p = (v_texCoords - 0.5) * normalizedSize;
    float radius = min(u_radius / minDim, min(normalizedSize.x, normalizedSize.y) * 0.5);
    vec2 halfSize = normalizedSize * 0.5;
    float dist = sdRoundedBox(p, halfSize, radius);
    if (u_topCornerRoundness <= 0.01 && u_bottomCornerRoundness >= 0.99) {
        dist = sdBottomRoundedBox(p, halfSize, radius);
    }
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
    if (u_hasSceneTexture > 0.5) {
        vec2 screenUv = clamp(gl_FragCoord.xy / max(u_screenSize, vec2(1.0)), vec2(0.001), vec2(0.999));
        vec2 liquidField = vec2(
            sin((shifted.y * 17.0 + shifted.x * 4.5) + u_time * 0.55) + cos((shifted.x * 10.0 - shifted.y * 7.5) - u_time * 0.37),
            cos((shifted.x * 14.0 + shifted.y * 3.0) - u_time * 0.43) + sin((shifted.y * 11.0 - shifted.x * 5.0) + u_time * 0.62)
        ) * 0.5;
        vec2 refractOffset = liquidField * u_distortion * 0.22;
        float blurRadius = (0.0012 + u_frost * 0.0014) * (0.74 + centerGlow * 0.24);
        vec2 horiz = vec2(blurRadius, 0.0);
        vec2 vert = vec2(0.0, blurRadius);
        vec2 diag = vec2(blurRadius * 0.7071, blurRadius * 0.7071);
        vec2 sampleUv = clamp(screenUv + refractOffset, vec2(0.001), vec2(0.999));
        vec3 refractedScene = texture2D(u_sceneTexture, sampleUv).rgb;
        vec3 blurredScene =
            texture2D(u_sceneTexture, clamp(sampleUv + horiz, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv - horiz, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv + vert, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv - vert, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv + diag, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv - diag, vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv + vec2(diag.x, -diag.y), vec2(0.001), vec2(0.999))).rgb +
            texture2D(u_sceneTexture, clamp(sampleUv + vec2(-diag.x, diag.y), vec2(0.001), vec2(0.999))).rgb;
        blurredScene /= 8.0;
        vec3 chromaticScene = vec3(
            texture2D(u_sceneTexture, clamp(sampleUv + horiz * 1.85 + vert * 0.20, vec2(0.001), vec2(0.999))).r,
            texture2D(u_sceneTexture, sampleUv).g,
            texture2D(u_sceneTexture, clamp(sampleUv - horiz * 1.85 - vert * 0.20, vec2(0.001), vec2(0.999))).b
        );
        float prismaticBand = smoothstep(0.10, 0.84, diagonal + liquid * 0.05) *
            (1.0 - smoothstep(0.62, 1.04, diagonal + liquid * 0.05));
        vec3 prismaticTint = vec3(
            0.5 + 0.5 * sin(diagonal * 16.0 + u_time * 0.30),
            0.5 + 0.5 * sin(diagonal * 16.0 + 2.10 + u_time * 0.30),
            0.5 + 0.5 * sin(diagonal * 16.0 + 4.20 + u_time * 0.30)
        );
        vec3 sceneGlass = mix(refractedScene, blurredScene, 0.50 + u_frost * 0.12);
        sceneGlass = mix(sceneGlass, chromaticScene, 0.22 + diagonalSheen * 0.20);
        vec3 liftedScene = mix(sceneGlass, pow(max(sceneGlass, vec3(0.0)), vec3(0.82)), 0.34 + hotspot * 0.12);
        vec3 milkyGlass = liftedScene + vec3(0.055);
        vec3 dispersionScene = abs(chromaticScene - refractedScene);
        color = milkyGlass + u_bodyTint.rgb * (0.08 + u_bodyTint.a * 0.10);
        color += dispersionScene * (0.05 + diagonalSheen * 0.07);
        color += prismaticTint * prismaticBand * (0.08 + diagonalSheen * 0.08 + hotspot * 0.04);
        color *= pressedShade;
    }
    color += u_edgeTint.rgb * rim * 0.16 * u_edgeTint.a;
    color += u_highlightTint.rgb * (diagonalSheen * 0.22 + hotspot * 0.18 + centerGlow * 0.09) * u_highlightTint.a;
    color += u_glowTint.rgb * frostNoise * u_frost * 0.05 * u_glowTint.a;

    float alpha = u_bodyTint.a * (0.96 + rim * 0.04) * mask;
    alpha += diagonalSheen * 0.01 * mask;
    alpha = clamp(alpha, 0.0, 1.0);

    gl_FragColor = vec4(color, alpha) * v_color;
}
"""
    }
}
