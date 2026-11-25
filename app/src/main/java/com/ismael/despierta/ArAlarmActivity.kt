package com.ismael.despierta

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Modo "tipo Pokémon GO": aparece un despertador virtual a 1–5 m (configurable)
 * en una dirección al azar. Hay que caminar hasta él y tocarlo para apagar la alarma.
 */
class ArAlarmActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    companion object {
        /** Qué tan cerca (m) hay que estar para poder tocarlo. */
        const val DISTANCIA_TOQUE = 0.8f
    }

    private lateinit var superficie: GLSurfaceView
    private lateinit var reloj: TextView
    private lateinit var txtInfo: TextView
    private lateinit var txtDistancia: TextView

    private var sesion: Session? = null
    private var instalacionPedida = false
    private val fondo = FondoCamara()
    private var texturaAsignada = false
    private var ancho = 1
    private var alto = 1
    private var geometriaCambio = false
    private var ancla: Anchor? = null
    private var distanciaObjetivo = 3f

    @Volatile private var distanciaActual = 99f
    @Volatile private var terminado = false

    private val vista = FloatArray(16)
    private val proyeccion = FloatArray(16)
    private val vp = FloatArray(16)
    private val punto = FloatArray(4)
    private val clip = FloatArray(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_ar)

        superficie = findViewById(R.id.superficie)
        reloj = findViewById(R.id.relojVirtual)
        txtInfo = findViewById(R.id.txtInfoRA)
        txtDistancia = findViewById(R.id.txtDistanciaRA)
        distanciaObjetivo = Prefs.raDistancia(this).toFloat()

        superficie.preserveEGLContextOnPause = true
        superficie.setEGLContextClientVersion(2)
        superficie.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        superficie.setRenderer(this)
        superficie.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // El despertador "tiembla" como si estuviera sonando
        ObjectAnimator.ofFloat(reloj, View.ROTATION, -14f, 14f).apply {
            duration = 120
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        reloj.setOnClickListener {
            if (distanciaActual <= DISTANCIA_TOQUE) apagar()
            else Toast.makeText(this, "¡Acércate más! Estás a %.1f m".format(distanciaActual), Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnVolverObjetos).setOnClickListener { finish() }

        vigilarAlarma()
    }

    /** Si la alarma se apagó por otro lado (o por límite de tiempo), cerramos. */
    private fun vigilarAlarma() {
        txtInfo.postDelayed({
            if (!AlarmService.sonando && !terminado) finish() else if (!terminado) vigilarAlarma()
        }, 2000)
    }

    override fun onResume() {
        super.onResume()
        if (sesion == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !instalacionPedida) ==
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    instalacionPedida = true
                    return
                }
                val s = Session(this)
                val cfg = Config(s)
                cfg.focusMode = Config.FocusMode.AUTO
                cfg.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                s.configure(cfg)
                sesion = s
            } catch (e: Exception) {
                Toast.makeText(this, "La realidad aumentada no está disponible: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        reanudarSesion(0)
    }

    /** La cámara puede tardar un instante en liberarse de la otra pantalla: reintentamos. */
    private fun reanudarSesion(intento: Int) {
        val s = sesion ?: return
        try {
            s.resume()
            superficie.onResume()
        } catch (e: CameraNotAvailableException) {
            if (intento < 10) superficie.postDelayed({ if (!isFinishing) reanudarSesion(intento + 1) }, 300)
            else { Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_LONG).show(); finish() }
        }
    }

    override fun onPause() {
        super.onPause()
        if (sesion != null) {
            superficie.onPause()
            sesion?.pause()
        }
    }

    override fun onDestroy() {
        sesion?.close()
        sesion = null
        super.onDestroy()
    }

    // ---------------- OpenGL ----------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        fondo.crear()
        texturaAsignada = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        ancho = width
        alto = height
        geometriaCambio = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = sesion ?: return
        if (!texturaAsignada) {
            s.setCameraTextureName(fondo.textura)
            texturaAsignada = true
        }
        if (geometriaCambio) {
            @Suppress("DEPRECATION")
            s.setDisplayGeometry(windowManager.defaultDisplay.rotation, ancho, alto)
            geometriaCambio = false
        }
        val frame = try { s.update() } catch (_: Exception) { return }
        fondo.dibujar(frame)

        val camara = frame.camera
        if (camara.trackingState != TrackingState.TRACKING) {
            runOnUiThread {
                reloj.visibility = View.INVISIBLE
                txtInfo.text = "Mueve el teléfono lentamente y apunta a un lugar con luz…"
                txtDistancia.text = ""
            }
            return
        }

        val poseCam = camara.pose
        if (ancla == null) ancla = colocarReloj(s, poseCam)
        val a = ancla ?: return
        val pa = a.pose

        // Distancia horizontal entre el teléfono y el despertador
        val dx = pa.tx() - poseCam.tx()
        val dz = pa.tz() - poseCam.tz()
        val dist = sqrt(dx * dx + dz * dz)
        distanciaActual = dist

        // Proyección a pantalla
        camara.getViewMatrix(vista, 0)
        camara.getProjectionMatrix(proyeccion, 0, 0.05f, 100f)
        Matrix.multiplyMM(vp, 0, proyeccion, 0, vista, 0)
        punto[0] = pa.tx(); punto[1] = pa.ty(); punto[2] = pa.tz(); punto[3] = 1f
        Matrix.multiplyMV(clip, 0, vp, 0, punto, 0)

        // Dirección relativa a donde apunta el teléfono
        val local = poseCam.inverse().transformPoint(floatArrayOf(pa.tx(), pa.ty(), pa.tz()))
        val angulo = Math.toDegrees(atan2(local[0].toDouble(), (-local[2]).toDouble())).toFloat()

        val delante = clip[3] > 0f
        val sx = if (delante) (clip[0] / clip[3] + 1f) / 2f * ancho else -1f
        val sy = if (delante) (1f - clip[1] / clip[3]) / 2f * alto else -1f
        val enPantalla = delante && sx in 0f..ancho.toFloat() && sy in 0f..alto.toFloat()
        val escala = (1.6f / dist.coerceAtLeast(0.3f)).coerceIn(0.35f, 2.6f)

        runOnUiThread {
            if (terminado) return@runOnUiThread
            txtDistancia.text = if (dist <= DISTANCIA_TOQUE) "✅ ¡Tócalo!" else "📏 %.1f m".format(dist)
            if (enPantalla) {
                reloj.visibility = View.VISIBLE
                reloj.scaleX = escala
                reloj.scaleY = escala
                reloj.translationX = sx - reloj.width / 2f
                reloj.translationY = sy - reloj.height / 2f
                txtInfo.text = if (dist <= DISTANCIA_TOQUE) "Toca el despertador para apagarlo"
                else "Camina hacia el despertador ⏰"
            } else {
                reloj.visibility = View.INVISIBLE
                txtInfo.text = when {
                    abs(angulo) > 135 -> "🔄 Está detrás de ti, date la vuelta"
                    angulo > 0 -> "➡️ Gira a la derecha"
                    else -> "⬅️ Gira a la izquierda"
                }
            }
        }
    }

    /** Pone el despertador a la distancia configurada, en una dirección al azar, a la altura del pecho. */
    private fun colocarReloj(s: Session, poseCam: Pose): Anchor? {
        val angulo = Random.nextDouble(0.0, 2 * Math.PI)
        val x = poseCam.tx() + (distanciaObjetivo * cos(angulo)).toFloat()
        val z = poseCam.tz() + (distanciaObjetivo * sin(angulo)).toFloat()
        val y = poseCam.ty() - 0.2f
        return try { s.createAnchor(Pose.makeTranslation(x, y, z)) } catch (_: Exception) { null }
    }

    private fun apagar() {
        if (terminado) return
        terminado = true
        stopService(Intent(this, AlarmService::class.java))
        Toast.makeText(this, "¡Buenos días! ☀️", Toast.LENGTH_LONG).show()
        finish()
    }
}
