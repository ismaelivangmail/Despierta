package com.ismael.despierta

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Dibuja la imagen de la cámara de ARCore como fondo de pantalla completa. */
class FondoCamara {
    var textura = 0
        private set
    private var programa = 0
    private var aPosicion = 0
    private var aTextura = 0

    private val vertices: FloatBuffer = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val coordsTextura: FloatBuffer = buffer(FloatArray(8))

    private fun buffer(datos: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(datos.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(datos); position(0)
        }

    fun crear() {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        textura = t[0]
        val destino = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(destino, textura)
        GLES20.glTexParameteri(destino, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(destino, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(destino, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(destino, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val vs = """
            attribute vec4 a_Posicion;
            attribute vec2 a_Textura;
            varying vec2 v_Textura;
            void main() { gl_Position = a_Posicion; v_Textura = a_Textura; }
        """.trimIndent()
        val fs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_Textura;
            uniform samplerExternalOES s_Textura;
            void main() { gl_FragColor = texture2D(s_Textura, v_Textura); }
        """.trimIndent()
        programa = GLES20.glCreateProgram()
        GLES20.glAttachShader(programa, compilar(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(programa, compilar(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(programa)
        aPosicion = GLES20.glGetAttribLocation(programa, "a_Posicion")
        aTextura = GLES20.glGetAttribLocation(programa, "a_Textura")
    }

    private fun compilar(tipo: Int, codigo: String): Int {
        val sh = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(sh, codigo)
        GLES20.glCompileShader(sh)
        return sh
    }

    fun dibujar(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            vertices.position(0)
            coordsTextura.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, vertices,
                Coordinates2d.TEXTURE_NORMALIZED, coordsTextura
            )
        }
        if (frame.timestamp == 0L) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textura)
        GLES20.glUseProgram(programa)

        vertices.position(0)
        coordsTextura.position(0)
        GLES20.glVertexAttribPointer(aPosicion, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(aTextura, 2, GLES20.GL_FLOAT, false, 0, coordsTextura)
        GLES20.glEnableVertexAttribArray(aPosicion)
        GLES20.glEnableVertexAttribArray(aTextura)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosicion)
        GLES20.glDisableVertexAttribArray(aTextura)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }
}
