package com.ismael.despierta

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clasifica imágenes con MobileNet V2 (ImageNet, cuantizado) en el propio teléfono, sin internet.
 */
class Clasificador(context: Context) {

    companion object { const val TAM = 224 }

    private val interprete: Interpreter
    private val etiquetas: List<String>
    private val entrada: ByteBuffer = ByteBuffer.allocateDirect(TAM * TAM * 3).order(ByteOrder.nativeOrder())
    private val salida: Array<ByteArray>
    private val pixeles = IntArray(TAM * TAM)

    init {
        val bytes = context.assets.open("mobilenet_v2_quant.tflite").use { it.readBytes() }
        val modelo = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        modelo.put(bytes)
        modelo.rewind()
        interprete = Interpreter(modelo, Interpreter.Options().setNumThreads(2))
        etiquetas = context.assets.open("labels.txt").bufferedReader().readLines()
            .map { it.split(",")[0].trim().lowercase() }
        salida = arrayOf(ByteArray(etiquetas.size))
    }

    /** Índices del modelo que corresponden a cada objeto. */
    fun indicesDe(objeto: Objeto): List<Int> =
        objeto.etiquetas.mapNotNull { e ->
            etiquetas.indexOf(e.lowercase()).takeIf { it >= 0 }
        }

    /** Devuelve la probabilidad (0..1) de cada clase. */
    fun clasificar(imagen: Bitmap): FloatArray {
        val escalada = Bitmap.createScaledBitmap(imagen, TAM, TAM, true)
        escalada.getPixels(pixeles, 0, TAM, 0, 0, TAM, TAM)
        entrada.rewind()
        for (p in pixeles) {
            entrada.put(((p shr 16) and 0xFF).toByte())
            entrada.put(((p shr 8) and 0xFF).toByte())
            entrada.put((p and 0xFF).toByte())
        }
        entrada.rewind()
        interprete.run(entrada, salida)
        val s = salida[0]
        return FloatArray(s.size) { (s[it].toInt() and 0xFF) / 256f }
    }

    fun cerrar() = interprete.close()
}
