package com.packabunch.ar

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * How one run of outline is drawn. Values come straight from the Figma frame
 * `ItemScan · Outline states`; lengths are in dp and converted with the screen density.
 */
data class OutlineStyle(
    val argb: Long,
    val widthDp: Float,
    /** Dash on/off lengths in dp; 0 on means solid. */
    val dashOnDp: Float = 0f,
    val dashOffDp: Float = 0f,
    /** Soft glow radius in dp (a Gaussian falloff each side), 0 for none. */
    val glowDp: Float = 0f,
    /** A second, wider and fainter glow — the measured state's "3 + 10". */
    val wideGlowDp: Float = 0f,
    /** Round dots rather than dashes (the amber "another angle" edge). */
    val dotted: Boolean = false,
) {
    companion object {
        val SCANNING = OutlineStyle(0xF2FFFFFF, 1.7f, dashOnDp = 6f, dashOffDp = 4f, glowDp = 5f)
        val ANOTHER_ANGLE_SEEN = SCANNING
        val ANOTHER_ANGLE_UNSEEN = OutlineStyle(0xFFF2A03D, 1.7f, dashOnDp = 2f, dashOffDp = 4f, glowDp = 5f, dotted = true)
        val MEASURED = OutlineStyle(0xFFFFFFFF, 2.2f, glowDp = 3f, wideGlowDp = 10f)
        const val MEASURED_FILL_ARGB = 0x29FFFFFFL   // 16 % white
        const val DRAW_ON_MS = 400L
    }
}

/**
 * Thin glowing outlines drawn over the camera, in screen space.
 *
 * Everything is projected on the CPU and handed to GL as pixel-space quads. That sounds
 * backwards, but it is what makes dashes behave: a dash pattern has to be measured along the
 * line *as it appears on screen*, continuously across the forty-eight short pieces of a can's
 * rim, and only the CPU knows where one piece ends and the next begins. There are a few
 * hundred vertices at most, so the cost is nothing.
 *
 * No depth test and no vignette: the outline sits on the picture, it does not darken it.
 */
internal class GlowOutlineRenderer {

    private var program = 0
    private var aPos = 0
    private var aAcross = 0
    private var aAlong = 0
    private var uViewport = 0
    private var uColor = 0
    private var uCore = 0
    private var uGlow = 0
    private var uWideGlow = 0
    private var uHalf = 0
    private var uDash = 0
    private var uDotted = 0
    private var uReveal = 0
    private var uPhase = 0

    private var fillProgram = 0
    private var fPos = 0
    private var fViewport = 0
    private var fColor = 0

    private var buffer: FloatBuffer = alloc(4096)

    fun createOnGlThread() {
        program = GlUtil.compileProgram(LINE_VS, LINE_FS)
        aPos = GLES20.glGetAttribLocation(program, "a_Pos")
        aAcross = GLES20.glGetAttribLocation(program, "a_Across")
        aAlong = GLES20.glGetAttribLocation(program, "a_Along")
        uViewport = GLES20.glGetUniformLocation(program, "u_Viewport")
        uColor = GLES20.glGetUniformLocation(program, "u_Color")
        uCore = GLES20.glGetUniformLocation(program, "u_Core")
        uGlow = GLES20.glGetUniformLocation(program, "u_Glow")
        uWideGlow = GLES20.glGetUniformLocation(program, "u_WideGlow")
        uHalf = GLES20.glGetUniformLocation(program, "u_Half")
        uDash = GLES20.glGetUniformLocation(program, "u_Dash")
        uDotted = GLES20.glGetUniformLocation(program, "u_Dotted")
        uReveal = GLES20.glGetUniformLocation(program, "u_Reveal")
        uPhase = GLES20.glGetUniformLocation(program, "u_Phase")

        fillProgram = GlUtil.compileProgram(FILL_VS, FILL_FS)
        fPos = GLES20.glGetAttribLocation(fillProgram, "a_Pos")
        fViewport = GLES20.glGetUniformLocation(fillProgram, "u_Viewport")
        fColor = GLES20.glGetUniformLocation(fillProgram, "u_Color")
        GlUtil.checkError("outline setup")
    }

    /**
     * Draws polylines already projected to pixels. Each polyline is a list of points; dash
     * phase runs continuously along it. [revealPx] clips drawing to that much length from the
     * start of each polyline (the draw-on animation); pass [Float.MAX_VALUE] for all of it.
     */
    fun drawPolylines(
        polylines: List<FloatArray>,
        style: OutlineStyle,
        density: Float,
        viewportW: Int,
        viewportH: Int,
        revealPx: Float = Float.MAX_VALUE,
        /**
         * Shifts the dash pattern along the line, in pixels. Advanced every frame while an
         * object is still being scanned, so the dashes march along its edges as points arrive
         * (`ItemScan · Outline states`, Scanning) and stop the moment it is measured.
         */
        dashPhasePx: Float = 0f,
    ) {
        if (polylines.isEmpty()) return
        val core = style.widthDp * density
        val glow = style.glowDp * density
        val wide = style.wideGlowDp * density
        val half = core / 2 + maxOf(glow, wide) * 1.6f + 1f

        var floats = 0
        for (p in polylines) floats += (p.size / 2 - 1).coerceAtLeast(0) * 6 * 4
        ensure(floats)
        buffer.clear()
        var vertices = 0
        for (p in polylines) {
            var along = 0f
            for (i in 0 until p.size / 2 - 1) {
                val x0 = p[2 * i]; val y0 = p[2 * i + 1]
                val x1 = p[2 * i + 2]; val y1 = p[2 * i + 3]
                val dx = x1 - x0; val dy = y1 - y0
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                if (len < 0.01f) continue
                // Extend each piece by the half-width at both ends so joins have no gaps.
                val tx = dx / len; val ty = dy / len
                val nx = -ty * half; val ny = tx * half
                val ex = tx * half; val ey = ty * half
                val a0 = along - half; val a1 = along + len + half
                fun v(x: Float, y: Float, across: Float, a: Float) { buffer.put(x); buffer.put(y); buffer.put(across); buffer.put(a); vertices++ }
                val ax = x0 - ex; val ay = y0 - ey; val bx = x1 + ex; val by = y1 + ey
                v(ax + nx, ay + ny, 1f, a0); v(ax - nx, ay - ny, -1f, a0); v(bx + nx, by + ny, 1f, a1)
                v(bx + nx, by + ny, 1f, a1); v(ax - nx, ay - ny, -1f, a0); v(bx - nx, by - ny, -1f, a1)
                along += len
            }
        }
        if (vertices == 0) return
        buffer.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uViewport, viewportW.toFloat(), viewportH.toFloat())
        setColor(uColor, style.argb)
        GLES20.glUniform1f(uCore, core)
        GLES20.glUniform1f(uGlow, glow)
        GLES20.glUniform1f(uWideGlow, wide)
        GLES20.glUniform1f(uHalf, half)
        GLES20.glUniform2f(uDash, style.dashOnDp * density, style.dashOffDp * density)
        GLES20.glUniform1f(uDotted, if (style.dotted) 1f else 0f)
        GLES20.glUniform1f(uReveal, revealPx)
        GLES20.glUniform1f(uPhase, dashPhasePx)

        val stride = 4 * Float.SIZE_BYTES
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(2)
        GLES20.glVertexAttribPointer(aAcross, 1, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aAlong, 1, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aAcross)
        GLES20.glEnableVertexAttribArray(aAlong)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aAcross)
        GLES20.glDisableVertexAttribArray(aAlong)
        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtil.checkError("outline draw")
    }

    /** A convex polygon in pixels, filled flat. The measured footprint's faint base. */
    fun drawFill(polygon: FloatArray, argb: Long, viewportW: Int, viewportH: Int) {
        val n = polygon.size / 2
        if (n < 3) return
        ensure((n - 2) * 3 * 2)
        buffer.clear()
        for (i in 1 until n - 1) {
            buffer.put(polygon[0]); buffer.put(polygon[1])
            buffer.put(polygon[2 * i]); buffer.put(polygon[2 * i + 1])
            buffer.put(polygon[2 * i + 2]); buffer.put(polygon[2 * i + 3])
        }
        buffer.position(0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(fillProgram)
        GLES20.glUniform2f(fViewport, viewportW.toFloat(), viewportH.toFloat())
        setColor(fColor, argb)
        GLES20.glVertexAttribPointer(fPos, 2, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glEnableVertexAttribArray(fPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, (n - 2) * 3)
        GLES20.glDisableVertexAttribArray(fPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun setColor(location: Int, argb: Long) {
        val a = ((argb shr 24) and 0xFF) / 255f
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        GLES20.glUniform4f(location, r, g, b, a)
    }

    private fun ensure(floats: Int) {
        if (buffer.capacity() < floats) buffer = alloc(floats * 2)
    }

    private companion object {
        fun alloc(floats: Int): FloatBuffer =
            ByteBuffer.allocateDirect(floats * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

        const val LINE_VS = """
            attribute vec2 a_Pos;
            attribute float a_Across;
            attribute float a_Along;
            uniform vec2 u_Viewport;
            varying float v_Across;
            varying float v_Along;
            void main() {
                vec2 ndc = vec2(a_Pos.x / u_Viewport.x * 2.0 - 1.0, 1.0 - a_Pos.y / u_Viewport.y * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
                v_Across = a_Across;
                v_Along = a_Along;
            }
        """

        // Distance from the centre line in pixels drives three layers: a crisp core, a tight
        // glow and an optional wide one. Premultiplied alpha, so glows add up without greying.
        const val LINE_FS = """
            precision mediump float;
            uniform vec4 u_Color;
            uniform float u_Core;
            uniform float u_Glow;
            uniform float u_WideGlow;
            uniform float u_Half;
            uniform vec2 u_Dash;
            uniform float u_Dotted;
            uniform float u_Reveal;
            uniform float u_Phase;
            varying float v_Across;
            varying float v_Along;
            void main() {
                if (v_Along > u_Reveal) discard;
                float d = abs(v_Across) * u_Half;
                float core = 1.0 - smoothstep(u_Core * 0.5 - 0.6, u_Core * 0.5 + 0.6, d);
                float glow = u_Glow > 0.0 ? 0.55 * exp(-(d * d) / (2.0 * u_Glow * u_Glow)) : 0.0;
                float wide = u_WideGlow > 0.0 ? 0.28 * exp(-(d * d) / (2.0 * u_WideGlow * u_WideGlow)) : 0.0;
                float a = max(core, glow + wide);
                if (u_Dash.x > 0.0) {
                    float period = u_Dash.x + u_Dash.y;
                    float t = mod(max(v_Along, 0.0) + u_Phase, period);
                    float on;
                    if (u_Dotted > 0.5) {
                        // A round dot of the dash length, centred in its period.
                        float c = t - u_Dash.x * 0.5;
                        float r = sqrt(c * c + d * d);
                        on = 1.0 - smoothstep(u_Core * 0.6, u_Core * 0.6 + 0.9, r);
                        a = max(on, on * (glow + wide));
                    } else {
                        on = 1.0 - smoothstep(u_Dash.x - 0.6, u_Dash.x + 0.6, t);
                        a *= on;
                    }
                }
                gl_FragColor = vec4(u_Color.rgb * u_Color.a * a, u_Color.a * a);
            }
        """

        const val FILL_VS = """
            attribute vec2 a_Pos;
            uniform vec2 u_Viewport;
            void main() {
                gl_Position = vec4(a_Pos.x / u_Viewport.x * 2.0 - 1.0, 1.0 - a_Pos.y / u_Viewport.y * 2.0, 0.0, 1.0);
            }
        """

        const val FILL_FS = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() { gl_FragColor = vec4(u_Color.rgb * u_Color.a, u_Color.a); }
        """
    }
}
