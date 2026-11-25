package com.ismael.despierta

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

class AlarmActivity : AppCompatActivity() {

    companion object {
        /** Confianza mínima para aceptar el objeto. */
        const val UMBRAL = 0.35f
        /** Cuadros seguidos en que debe verse el objeto (evita falsos positivos). */
        const val CUADROS_SEGUIDOS = 3
    }

    private lateinit var vistaCamara: PreviewView
    private lateinit var txtReloj: TextView
    private lateinit var txtDeteccion: TextView
    private lateinit var barra: ProgressBar

    private var clasificador: Clasificador? = null
    private lateinit var ejecutor: ExecutorService
    private var objetivos: List<Pair<Objeto, List<Int>>> = emptyList()
    private var seguidos = 0
    @Volatile private var terminado = false
    private var vioSonando = false

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara()
        else txtDeteccion.text = "Necesito la cámara para apagar la alarma. Actívala en Ajustes."
    }

    private val tick = object : Runnable {
        override fun run() {
            txtReloj.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            if (AlarmService.sonando) vioSonando = true
            if (vioSonando && !AlarmService.sonando && !terminado) {
                // La alarma se detuvo sola (límite de tiempo): cerramos la pantalla
                finish()
                return
            }
            txtReloj.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarSobreBloqueo()
        setContentView(R.layout.activity_alarm)

        vistaCamara = findViewById(R.id.vistaCamara)
        txtReloj = findViewById(R.id.txtReloj)
        txtDeteccion = findViewById(R.id.txtDeteccion)
        barra = findViewById(R.id.barraDeteccion)

        // No se puede salir con el botón atrás
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, "Muestra un objeto para apagarla 😉", Toast.LENGTH_SHORT).show()
            }
        })

        ejecutor = Executors.newSingleThreadExecutor()
        val c = Clasificador(this)
        clasificador = c
        objetivos = Prefs.objetos(this)
            .mapNotNull { Objetos.porId(it) }
            .ifEmpty { Objetos.predeterminados.mapNotNull { Objetos.porId(it) } }
            .map { it to c.indicesDe(it) }

        findViewById<TextView>(R.id.txtObjetivos).text =
            objetivos.joinToString("\n") { "${it.first.emoji}  ${it.first.nombre}" }

        txtReloj.post(tick)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            pedirCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun mostrarSobreBloqueo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val proveedor = futuro.get()
            val vista = Preview.Builder().build().also { it.setSurfaceProvider(vistaCamara.surfaceProvider) }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analisis.setAnalyzer(ejecutor) { analizar(it) }
            try {
                proveedor.unbindAll()
                proveedor.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vista, analisis)
            } catch (e: Exception) {
                txtDeteccion.text = "No se pudo abrir la cámara: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analizar(img: ImageProxy) {
        try {
            if (terminado) return
            val c = clasificador ?: return
            val cuadro = recortarCuadrado(img.toBitmap(), img.imageInfo.rotationDegrees)
            val prob = c.clasificar(cuadro)

            var mejor: Objeto? = null
            var mejorValor = 0f
            for ((obj, indices) in objetivos) {
                val v = indices.sumOf { prob[it].toDouble() }.toFloat()
                if (v > mejorValor) { mejorValor = v; mejor = obj }
            }

            seguidos = if (mejorValor >= UMBRAL) seguidos + 1 else 0
            val listo = seguidos >= CUADROS_SEGUIDOS
            val obj = mejor
            val valor = mejorValor

            runOnUiThread {
                if (terminado) return@runOnUiThread
                val pct = (min(valor / UMBRAL, 1f) * 100).roundToInt()
                barra.progress = pct
                txtDeteccion.text = if (obj != null && valor > 0.05f)
                    "Parece ${obj.emoji} ${obj.nombre}… ${(valor * 100).roundToInt()}%"
                else "Buscando…"
                if (listo) apagar(obj)
            }
        } catch (_: Exception) {
        } finally {
            img.close()
        }
    }

    private fun recortarCuadrado(bmp: Bitmap, rotacion: Int): Bitmap {
        val lado = min(bmp.width, bmp.height)
        val x = (bmp.width - lado) / 2
        val y = (bmp.height - lado) / 2
        val m = Matrix().apply { postRotate(rotacion.toFloat()) }
        return Bitmap.createBitmap(bmp, x, y, lado, lado, m, true)
    }

    private fun apagar(obj: Objeto?) {
        if (terminado) return
        terminado = true
        stopService(Intent(this, AlarmService::class.java))
        Toast.makeText(this, "¡Buenos días! ${obj?.emoji ?: "☀️"}", Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        finish()
    }

    override fun onDestroy() {
        txtReloj.removeCallbacks(tick)
        ejecutor.shutdown()
        super.onDestroy()
        try { ejecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        clasificador?.cerrar()
        clasificador = null
    }
}
