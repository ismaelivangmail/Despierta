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

class AlarmActivity : AlarmaBaseActivity() {

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
    private var proveedor: ProcessCameraProvider? = null
    private lateinit var ejecutor: ExecutorService
    private var objetivos: List<Pair<Objeto, List<Int>>> = emptyList()
    private var seguidos = 0
    @Volatile private var terminado = false
    private var vioSonando = false
    private var nombreAlarma: String? = null

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara()
        else txtDeteccion.text = "Necesito la cámara para apagar la alarma. Actívala en Ajustes."
    }

    private val tick = object : Runnable {
        override fun run() {
            val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            txtReloj.text = if (nombreAlarma.isNullOrBlank()) hora else "$hora\n$nombreAlarma"
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
        Alarmas.mostrarSobreBloqueo(this)
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
        objetivos = Objetos.sorteoActual(this).map { it to c.indicesDe(it) }

        findViewById<TextView>(R.id.txtObjetivos).text =
            objetivos.joinToString("\n") { "${it.first.emoji}  ${it.first.nombre}" }

        txtReloj.post(tick)
        if (savedInstanceState == null) animarIntro() else findViewById<android.view.View>(R.id.capaIntro).visibility = android.view.View.GONE

        val alarma = Alarmas.sonando(this)
        val btnVirtual = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVirtual)
        val btnPasos = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPasos)
        if (alarma?.virtual == true) {
            btnVirtual.visibility = android.view.View.VISIBLE
            btnVirtual.text = "🧭 Buscar el despertador virtual (${alarma.distancia} m)"
            btnVirtual.setOnClickListener {
                proveedor?.unbindAll()
                startActivity(Intent(this, VirtualActivity::class.java))
            }
        }
        if (alarma?.pasos == true) {
            btnPasos.visibility = android.view.View.VISIBLE
            btnPasos.text = "👣 Caminar ${alarma.numPasos} pasos"
            btnPasos.setOnClickListener { startActivity(Intent(this, PasosActivity::class.java)) }
        }
        val nombre = alarma?.nombre?.takeIf { it.isNotBlank() }
        nombreAlarma = nombre

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pedirCamara.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!terminado && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        }
    }

    override fun onPause() {
        super.onPause()
        proveedor?.unbindAll()
    }

    /** Pantalla de entrada: el godín temblando, el fondo parpadeando como alarma y el texto saltando. */
    private fun animarIntro() {
        val capa = findViewById<android.view.View>(R.id.capaIntro)
        val godin = findViewById<android.view.View>(R.id.imgGodin)
        val texto = findViewById<android.view.View>(R.id.txtIntro)
        val animaciones = mutableListOf<android.animation.Animator>()

        // Temblor 🫨
        animaciones += android.animation.ObjectAnimator.ofFloat(godin, android.view.View.ROTATION, -12f, 12f).apply {
            duration = 70; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }
        animaciones += android.animation.ObjectAnimator.ofFloat(godin, android.view.View.TRANSLATION_X, -10f, 10f).apply {
            duration = 55; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }
        // Latido
        animaciones += android.animation.AnimatorSet().apply {
            playTogether(
                android.animation.ObjectAnimator.ofFloat(godin, android.view.View.SCALE_X, 0.9f, 1.1f),
                android.animation.ObjectAnimator.ofFloat(godin, android.view.View.SCALE_Y, 0.9f, 1.1f)
            )
            childAnimations.forEach {
                (it as android.animation.ValueAnimator).apply {
                    duration = 400; repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                }
            }
        }
        // Texto saltando
        animaciones += android.animation.ObjectAnimator.ofFloat(texto, android.view.View.TRANSLATION_Y, 0f, -24f).apply {
            duration = 300; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        // Fondo parpadeando rojo / naranja
        animaciones += android.animation.ValueAnimator.ofArgb(0xFFB71C1C.toInt(), 0xFFFF6D00.toInt()).apply {
            duration = 350; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { capa.setBackgroundColor(it.animatedValue as Int) }
        }
        animaciones.forEach { it.start() }

        var cerrada = false
        val cerrar = {
            if (!cerrada) {
                cerrada = true
                capa.animate().alpha(0f).setDuration(400).withEndAction {
                    capa.visibility = android.view.View.GONE
                    animaciones.forEach { it.cancel() }
                }.start()
            }
        }
        capa.setOnClickListener { cerrar() }
        capa.postDelayed({ cerrar() }, 4000)
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            if (isFinishing) return@addListener
            val proveedor = futuro.get()
            this.proveedor = proveedor
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
        proveedor?.unbindAll()
        Alarmas.apagar(this, obj?.emoji ?: "☀️")
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
