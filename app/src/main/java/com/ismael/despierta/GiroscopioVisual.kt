package com.ismael.despierta

import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * "Giroscopio" hecho con la cámara: compara cada imagen con la anterior y calcula
 * cuánto se desplazó el fondo a lo ancho de la pantalla. Si el fondo se mueve a la
 * izquierda, el teléfono giró a la derecha. Para teléfonos sin giroscopio ni brújula.
 */
class GiroscopioVisual {

    companion object {
        const val ANCHO = 80          // columnas de la imagen reducida (horizontal de pantalla)
        const val ALTO = 60
        const val BUSQUEDA = 12       // desplazamiento máximo buscado por cuadro
        const val CAMPO_IMAGEN = 50f  // grados que abarca la imagen a lo ancho (aprox.)
    }

    private var anterior: FloatArray? = null
    private val actual = FloatArray(ANCHO * ALTO)

    /** Ancho (en píxeles de pantalla vertical) de la imagen completa de la cámara. */
    @Volatile var anchoImagen = 0
    @Volatile var altoImagen = 0

    /** Devuelve cuántos grados giró el teléfono desde el cuadro anterior (+ = derecha). */
    fun procesar(img: ImageProxy): Float {
        val plano = img.planes[0]
        val buf = plano.buffer
        val paso = plano.rowStride
        val px = plano.pixelStride
        val iw = img.width
        val ih = img.height
        val rot = img.imageInfo.rotationDegrees
        val girada = rot == 90 || rot == 270
        anchoImagen = if (girada) ih else iw
        altoImagen = if (girada) iw else ih

        // Reducir a ANCHO x ALTO en orientación de pantalla (promedio de 3x3 muestras)
        var suma = 0f
        for (j in 0 until ALTO) {
            for (i in 0 until ANCHO) {
                var acc = 0
                for (a in 0..2) for (b in 0..2) {
                    val fx = (i * 3 + a) / (ANCHO * 3f)   // 0..1 a lo ancho de la pantalla
                    val fy = (j * 3 + b) / (ALTO * 3f)    // 0..1 a lo alto de la pantalla
                    val sx: Int
                    val sy: Int
                    when (rot) {
                        90 -> { sx = (fy * iw).toInt(); sy = ((1f - fx) * ih).toInt() }
                        270 -> { sx = ((1f - fy) * iw).toInt(); sy = (fx * ih).toInt() }
                        180 -> { sx = ((1f - fx) * iw).toInt(); sy = ((1f - fy) * ih).toInt() }
                        else -> { sx = (fx * iw).toInt(); sy = (fy * ih).toInt() }
                    }
                    val x = sx.coerceIn(0, iw - 1)
                    val y = sy.coerceIn(0, ih - 1)
                    acc += buf.get(y * paso + x * px).toInt() and 0xFF
                }
                val v = acc / 9f
                actual[j * ANCHO + i] = v
                suma += v
            }
        }
        // Quitar el brillo promedio (la cámara ajusta la exposición sola)
        val media = suma / (ANCHO * ALTO)
        for (k in actual.indices) actual[k] -= media

        val prev = anterior
        anterior = actual.copyOf()
        if (prev == null) return 0f

        // Buscar el desplazamiento horizontal que mejor empata ambas imágenes
        val errores = FloatArray(2 * BUSQUEDA + 1)
        var mejor = 0
        var mejorErr = Float.MAX_VALUE
        var peorErr = 0f
        for (s in -BUSQUEDA..BUSQUEDA) {
            var err = 0f
            var n = 0
            val iIni = maxOf(0, -s)
            val iFin = minOf(ANCHO, ANCHO - s)
            for (j in 2 until ALTO - 2) {
                val fila = j * ANCHO
                for (i in iIni until iFin) {
                    err += abs(actual[fila + i + s] - prev[fila + i])
                    n++
                }
            }
            err /= n.coerceAtLeast(1)
            errores[s + BUSQUEDA] = err
            if (err < mejorErr) { mejorErr = err; mejor = s }
            if (err > peorErr) peorErr = err
        }
        // Imagen sin textura (muy oscura o pared lisa): no se puede saber si giró
        if (peorErr - mejorErr < 1.5f) return 0f

        // Ajuste fino (subpíxel) con una parábola
        var desp = mejor.toFloat()
        if (mejor > -BUSQUEDA && mejor < BUSQUEDA) {
            val a = errores[mejor + BUSQUEDA - 1]
            val b = errores[mejor + BUSQUEDA]
            val c = errores[mejor + BUSQUEDA + 1]
            val den = a - 2 * b + c
            if (den > 0f) desp += (a - c) / (2 * den)
        }
        // actual[i + s] ≈ anterior[i]: la escena se movió s columnas a la DERECHA si s > 0,
        // lo que significa que el teléfono giró a la IZQUIERDA (grados negativos)
        val grados = -desp * CAMPO_IMAGEN / ANCHO
        return if (abs(grados) < 0.08f) 0f else grados
    }
}
