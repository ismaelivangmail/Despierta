package com.ismael.despierta

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pantalla de la alarma sonando. Todo funciona al mismo tiempo, sin elegir:
 * la cámara busca los objetos y, si la alarma lo permite, los pasos se cuentan solos.
 */
class AlarmActivity : AlarmaBaseActivity() {

    companion object {
        const val UMBRAL = 0.35f
        const val CUADROS_SEGUIDOS = 3
    }

    private lateinit var vistaCamara: PreviewView
    private lateinit var txtReloj: TextView
    private lateinit var txtDeteccion: TextView
    private lateinit var barra: ProgressBar
    private lateinit var txtPasos: TextView
    private lateinit var barraPasos: ProgressBar

    private var clasificador: Clasificador? = null
    private var proveedor: ProcessCameraProvider? = null
    private lateinit var ejecutor: ExecutorService
    private var objetivos: List<Pair<Objeto, List<Int>>> = emptyList()
    private var seguidos = 0
    @Volatile private var terminado = false
    private var vioSonando = false

    private var contador: ContadorPasos? = null
    private var pasos = 0
    private var metaPasos = 0
    private val animaciones = mutableListOf<Animator>()

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamara()
        else txtDeteccion.text = "Necesito la cámara para los objetos. Actívala en Ajustes."
    }

    private val tick = object : Runnable {
        override fun run() {
            txtReloj.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            if (AlarmService.sonando) vioSonando = true
            if (vioSonando && !AlarmService.sonando && !terminado) {
                finish()   // se apagó por otro lado o por límite de tiempo
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
        txtPasos = findViewById(R.id.txtPasos)
        barraPasos = findViewById(R.id.barraPasos)

        // Esquinas redondeadas para la cámara
        findViewById<View>(R.id.marcoCamara).clipToOutline = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, "¡Nada de volverse a dormir! 😉", Toast.LENGTH_SHORT).show()
            }
        })

        ejecutor = Executors.newSingleThreadExecutor()
        val c = Clasificador(this)
        clasificador = c
        objetivos = Objetos.sorteoActual(this).map { it to c.indicesDe(it) }
        findViewById<TextView>(R.id.txtObjetivos).text =
            objetivos.joinToString("\n") { "${it.first.emoji}  ${it.first.nombre}" }

        val alarma = Alarmas.sonando(this)
        alarma?.nombre?.takeIf { it.isNotBlank() }?.let { findViewById<TextView>(R.id.txtNombre).text = it }

        // Pasos: se cuentan solos, sin tocar nada
        if (alarma?.pasos == true) {
            metaPasos = alarma.numPasos
            pasos = savedInstanceState?.getInt("pasos") ?: 0
            findViewById<View>(R.id.filaPasos).visibility = View.VISIBLE
            contador = ContadorPasos(this) {
                runOnUiThread {
                    if (terminado) return@runOnUiThread
                    pasos++
                    mostrarPasos()
                    if (pasos >= metaPasos) apagar("👣")
                }
            }
            mostrarPasos()
        }

        // Despertador virtual: necesita su propia pantalla para usar la cámara
        if (alarma?.virtual == true) {
            val btn = findViewById<MaterialButton>(R.id.btnVirtual)
            btn.visibility = View.VISIBLE
            btn.text = "🧭 Buscar el despertador virtual (${alarma.distancia} m)"
            btn.setOnClickListener {
                proveedor?.unbindAll()
                startActivity(Intent(this, VirtualActivity::class.java))
            }
        }

        txtReloj.post(tick)
        animarGodin()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pedirCamara.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pasos", pasos)
    }

    private fun mostrarPasos() {
        txtPasos.text = "👣 Caminando también se apaga: $pasos / $metaPasos pasos"
        barraPasos.progress = if (metaPasos > 0) (pasos * 100 / metaPasos).coerceAtMost(100) else 0
    }

    override fun onResume() {
        super.onResume()
        contador?.iniciar()
        if (!terminado && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        }
    }

    override fun onPause() {
        super.onPause()
        contador?.detener()
        proveedor?.unbindAll()
    }

    /** El godín tiembla 🫨 todo el tiempo, el texto salta y el fondo parpadea como alarma. */
    private fun animarGodin() {
        val godin = findViewById<View>(R.id.imgGodin)
        val texto = findViewById<View>(R.id.txtIntro)
        val raiz = findViewById<View>(R.id.raiz)

        fun infinita(a: ValueAnimator, ms: Long) = a.apply {
            duration = ms
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        animaciones += infinita(ObjectAnimator.ofFloat(godin, View.ROTATION, -12f, 12f), 70)
        animaciones += infinita(ObjectAnimator.ofFloat(godin, View.TRANSLATION_X, -8f, 8f), 55)
        animaciones += AnimatorSet().apply {
            playTogether(
                infinita(ObjectAnimator.ofFloat(godin, View.SCALE_X, 0.92f, 1.08f), 400),
                infinita(ObjectAnimator.ofFloat(godin, View.SCALE_Y, 0.92f, 1.08f), 400)
            )
        }
        animaciones += infinita(ObjectAnimator.ofFloat(texto, View.TRANSLATION_Y, 0f, -10f), 300).apply {
            interpolator = DecelerateInterpolator()
        }
        animaciones += infinita(ValueAnimator.ofArgb(0xFFB71C1C.toInt(), 0xFFE65100.toInt()), 700).apply {
            addUpdateListener { raiz.setBackgroundColor(it.animatedValue as Int) }
        }
        animaciones.forEach { it.start() }
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            if (isFinishing) return@addListener
            val p = futuro.get()
            proveedor = p
            val vista = Preview.Builder().build().also { it.setSurfaceProvider(vistaCamara.surfaceProvider) }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analisis.setAnalyzer(ejecutor) { analizar(it) }
            try {
                p.unbindAll()
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vista, analisis)
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
                barra.progress = (min(valor / UMBRAL, 1f) * 100).roundToInt()
                txtDeteccion.text = if (obj != null && valor > 0.05f)
                    "📷 Parece ${obj.emoji} ${obj.nombre}… ${(valor * 100).roundToInt()}%"
                else "📷 Apunta a uno de los objetos…"
                if (listo) apagar(obj?.emoji ?: "☀️")
            }
        } catch (_: Exception) {
        } finally {
            img.close()
        }
    }

    private fun recortarCuadrado(bmp: Bitmap, rotacion: Int): Bitmap {
        val lado = min(bmp.width, bmp.height)
        val m = Matrix().apply { postRotate(rotacion.toFloat()) }
        return Bitmap.createBitmap(bmp, (bmp.width - lado) / 2, (bmp.height - lado) / 2, lado, lado, m, true)
    }

    private fun apagar(emoji: String) {
        if (terminado) return
        terminado = true
        contador?.detener()
        proveedor?.unbindAll()
        Alarmas.apagar(this, emoji)
    }

    override fun onDestroy() {
        animaciones.forEach { it.cancel() }
        txtReloj.removeCallbacks(tick)
        ejecutor.shutdown()
        super.onDestroy()
        try { ejecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        clasificador?.cerrar()
        clasificador = null
    }
}
