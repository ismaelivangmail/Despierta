package com.ismael.despierta

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reto estilo Pokémon GO (sin ARCore): el despertador aparece en una dirección al azar.
 * Hay que girar con el teléfono hasta verlo y caminar hacia él los pasos equivalentes
 * a la distancia configurada. Los pasos solo cuentan mientras lo tienes enfrente.
 */
class VirtualActivity : AlarmaBaseActivity(), SensorEventListener {

    companion object {
        const val METROS_POR_PASO = 0.7f
        const val CAMPO_VISION = 60f   // grados que abarca la pantalla a lo ancho
        const val TOLERANCIA_CENTRO = 12f   // grados para considerar que está en el centro
        const val CUADROS_PARA_ATRAPAR = 12 // ~medio segundo centrado
    }

    private lateinit var vistaCamara: PreviewView
    private lateinit var reloj: TextView
    private lateinit var txtInfo: TextView
    private lateinit var txtProgreso: TextView
    private lateinit var barra: ProgressBar

    private lateinit var sm: SensorManager
    private lateinit var contador: ContadorPasos
    private var proveedor: ProcessCameraProvider? = null

    /** true = el teléfono no tiene giroscopio ni brújula: se usa la cámara para saber si giras. */
    private var modoCamara = false
    private val giroVisual = GiroscopioVisual()
    private lateinit var ejecutor: ExecutorService

    private val matriz = FloatArray(9)
    private val remapeada = FloatArray(9)
    private val orientacion = FloatArray(3)
    private var gravedad: FloatArray? = null
    private var magnetico: FloatArray? = null

    private var rumbo = Float.NaN          // hacia dónde apunta la cámara (grados)
    private var objetivo = Float.NaN       // dónde está el despertador (grados)
    private var pasosNecesarios = 4
    private var pasos = 0
    private var pasosTotales = 0
    private var atrapado = false
    private var cuadrosCentrado = 0
    private lateinit var txtDiag: TextView
    private var terminado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarmas.mostrarSobreBloqueo(this)
        setContentView(R.layout.activity_virtual)
        vistaCamara = findViewById(R.id.vistaCamara)
        reloj = findViewById(R.id.relojVirtual)
        txtInfo = findViewById(R.id.txtInfo)
        txtProgreso = findViewById(R.id.txtProgreso)
        barra = findViewById(R.id.barraCamino)
        txtDiag = findViewById(R.id.txtDiag)

        val metros = Alarmas.sonando(this)?.distancia ?: 3
        pasosNecesarios = ceil(metros / METROS_POR_PASO).toInt().coerceAtLeast(2)
        if (savedInstanceState != null) {
            objetivo = savedInstanceState.getFloat("objetivo", Float.NaN)
            pasos = savedInstanceState.getInt("pasos", 0)
            atrapado = savedInstanceState.getBoolean("atrapado", false)
        }

        ObjectAnimator.ofFloat(reloj, View.ROTATION, -14f, 14f).apply {
            duration = 120
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        reloj.setOnClickListener {
            if (pasos >= pasosNecesarios) {
                terminado = true
                Alarmas.apagar(this, "⏰")
            } else {
                Toast.makeText(this,
                    if (atrapado) "¡Todavía está lejos! Camina hacia él" else "Primero ponlo en el centro de la pantalla",
                    Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnVolver).setOnClickListener { finish() }

        sm = getSystemService(SensorManager::class.java)
        modoCamara = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null &&
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null
        ejecutor = Executors.newSingleThreadExecutor()
        if (modoCamara) {
            rumbo = 0f
            if (objetivo.isNaN()) objetivo = normalizar(Random.nextInt(70, 291).toFloat())
            txtInfo.text = "Gira despacio para buscar el despertador…"
        }
        contador = ContadorPasos(this) { runOnUiThread { alDarPaso() } }
        actualizarProgreso()
        vigilar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("objetivo", objetivo)
        outState.putInt("pasos", pasos)
        outState.putBoolean("atrapado", atrapado)
    }

    override fun onResume() {
        super.onResume()
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv != null) {
            sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME)
        } else if (!modoCamara) {
            // Sin giroscopio pero con brújula: brújula + acelerómetro
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        contador.iniciar()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        }
    }

    override fun onDestroy() {
        ejecutor.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
        contador.detener()
        proveedor?.unbindAll()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            if (isFinishing) return@addListener
            val p = futuro.get()
            proveedor = p
            val vista = Preview.Builder().build().also { it.setSurfaceProvider(vistaCamara.surfaceProvider) }
            try {
                p.unbindAll()
                if (modoCamara) {
                    val analisis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analisis.setAnalyzer(ejecutor) { img ->
                        try {
                            val giro = giroVisual.procesar(img)
                            runOnUiThread {
                                if (!rumbo.isNaN()) {
                                    rumbo = normalizar(rumbo + giro)
                                    dibujar()
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            img.close()
                        }
                    }
                    p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vista, analisis)
                } else {
                    p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vista)
                }
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun vigilar() {
        txtInfo.postDelayed({
            if (!AlarmService.sonando && !terminado) finish() else if (!terminado) vigilar()
        }, 2000)
    }

    // ---------- Orientación ----------

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(matriz, e.values)
            Sensor.TYPE_ACCELEROMETER -> { gravedad = e.values.clone(); if (!matrizBrujula()) return }
            Sensor.TYPE_MAGNETIC_FIELD -> { magnetico = e.values.clone(); if (!matrizBrujula()) return }
            else -> return
        }
        // Teléfono vertical con la cámara al frente
        SensorManager.remapCoordinateSystem(matriz, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapeada)
        SensorManager.getOrientation(remapeada, orientacion)
        val nuevo = Math.toDegrees(orientacion[0].toDouble()).toFloat()
        rumbo = if (rumbo.isNaN()) nuevo else suavizar(rumbo, nuevo, 0.15f)

        if (objetivo.isNaN()) {
            // Lejos de donde apuntas ahora (entre 70° y 290°), para que tengas que girar
            objetivo = normalizar(rumbo + Random.nextInt(70, 291))
        }
        dibujar()
    }

    private fun matrizBrujula(): Boolean {
        val g = gravedad ?: return false
        val m = magnetico ?: return false
        return SensorManager.getRotationMatrix(matriz, null, g, m)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun normalizar(a: Float): Float {
        var x = a % 360f
        if (x > 180f) x -= 360f
        if (x < -180f) x += 360f
        return x
    }

    private fun suavizar(actual: Float, nuevo: Float, k: Float): Float =
        normalizar(actual + normalizar(nuevo - actual) * k)

    private fun diferencia(): Float = normalizar(objetivo - rumbo)

    /**
     * Fases: 1) BUSCAR girando hasta tenerlo en el centro; 2) al encontrarlo queda "atrapado"
     * en el centro y solo hay que CAMINAR los pasos; 3) al llegar, tocarlo.
     * Así ya no se mueve de lugar mientras caminas (al caminar el teléfono se bambolea).
     */
    private fun dibujar() {
        if (terminado || rumbo.isNaN() || objetivo.isNaN()) return
        val ancho = (reloj.parent as View).width.toFloat()
        val alto = (reloj.parent as View).height.toFloat()
        val progreso = (pasos.toFloat() / pasosNecesarios).coerceAtMost(1f)
        val llego = pasos >= pasosNecesarios

        txtDiag.text = (if (modoCamara) "📷 " else "🧭 ") + "%d°  ·  ⏰ %d°  ·  pasos detectados: %d".format(
            normalizar360(rumbo), normalizar360(objetivo), pasosTotales)

        if (atrapado) {
            reloj.visibility = View.VISIBLE
            val escala = 0.9f + 1.4f * progreso
            reloj.scaleX = escala
            reloj.scaleY = escala
            reloj.translationX = ancho / 2f - reloj.width / 2f
            reloj.translationY = alto * 0.5f - reloj.height / 2f
            txtInfo.text = if (llego) "¡Llegaste! Toca el despertador ⏰" else "🚶 ¡Lo encontraste! Camina hacia él"
            return
        }

        val dif = diferencia()
        val x = ancho / 2f + dif * pixelesPorGrado(ancho, alto)
        if (x > -reloj.width && x < ancho + reloj.width) {
            reloj.visibility = View.VISIBLE
            reloj.scaleX = 0.8f
            reloj.scaleY = 0.8f
            reloj.translationX = x - reloj.width / 2f
            reloj.translationY = alto * 0.5f - reloj.height / 2f
        } else {
            reloj.visibility = View.INVISIBLE
        }

        // ¿Ya lo tiene enfrente? Si se mantiene centrado un momento, queda atrapado.
        if (abs(dif) <= TOLERANCIA_CENTRO) {
            cuadrosCentrado++
            if (cuadrosCentrado >= CUADROS_PARA_ATRAPAR) {
                atrapado = true
                reloj.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                dibujar()
                return
            }
            txtInfo.text = "🎯 ¡Ahí está! Mantenlo en el centro…"
        } else {
            cuadrosCentrado = 0
            txtInfo.text = when {
                abs(dif) > 135 -> "🔄 Está detrás de ti, date la vuelta"
                dif > 0 -> "➡️ Gira a la derecha"
                else -> "⬅️ Gira a la izquierda"
            }
        }
    }

    /**
     * Cuántos píxeles de pantalla equivalen a un grado de giro.
     * En modo cámara se calcula con el tamaño real con que se muestra la imagen, así el
     * despertador se mueve exactamente igual que el fondo.
     */
    private fun pixelesPorGrado(ancho: Float, alto: Float): Float {
        val iw = giroVisual.anchoImagen
        val ih = giroVisual.altoImagen
        if (!modoCamara || iw == 0 || ih == 0) return ancho / CAMPO_VISION
        val escala = maxOf(ancho / iw, alto / ih)   // la vista previa llena la pantalla
        return iw * escala / GiroscopioVisual.CAMPO_IMAGEN
    }

    private fun normalizar360(a: Float): Int = ((a % 360f + 360f) % 360f).toInt()

    private fun alDarPaso() {
        pasosTotales++
        if (terminado || !atrapado || pasos >= pasosNecesarios) return
        pasos++
        actualizarProgreso()
        dibujar()
    }

    private fun actualizarProgreso() {
        val restantes = (pasosNecesarios - pasos).coerceAtLeast(0)
        txtProgreso.text = if (restantes == 0) "✅ Estás junto a él"
        else "📏 Faltan %.1f m (%d pasos)".format(restantes * METROS_POR_PASO, restantes)
        barra.progress = pasos * 100 / pasosNecesarios
    }
}
