package com.ismael.despierta

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var txtProxima: TextView
    private lateinit var txtVacio: TextView
    private lateinit var btnPantallaCompleta: MaterialButton

    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lista = findViewById(R.id.listaAlarmas)
        txtProxima = findViewById(R.id.txtProxima)
        txtVacio = findViewById(R.id.txtVacio)
        btnPantallaCompleta = findViewById(R.id.btnPantallaCompleta)

        findViewById<View>(R.id.btnAgregar).setOnClickListener {
            startActivity(Intent(this, EditarActivity::class.java))
        }
        btnPantallaCompleta.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        findViewById<TextView>(R.id.txtVersion).text = "Versión ${Actualizador.nombreActual(this)}"
        findViewById<MaterialButton>(R.id.btnActualizar).setOnClickListener { buscarActualizacion(true) }

        prepararVolumen()
        Programador.programarTodas(this)
        pedirPermisosNecesarios()
        if (savedInstanceState == null) buscarActualizacion(false)
    }

    override fun onResume() {
        super.onResume()
        pintarLista()
        revisarPantallaCompleta()
        instalarPendiente()
    }

    // ---------- Lista ----------

    private fun pintarLista() {
        lista.removeAllViews()
        val alarmas = Prefs.alarmas(this)
        txtVacio.visibility = if (alarmas.isEmpty()) View.VISIBLE else View.GONE
        lista.visibility = if (alarmas.isEmpty()) View.GONE else View.VISIBLE
        val formato24 = DateFormat.is24HourFormat(this)

        alarmas.forEachIndexed { i, a ->
            if (i > 0) {
                lista.addView(View(this).apply {
                    setBackgroundColor(0xFF3A3B40.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                })
            }
            val v = layoutInflater.inflate(R.layout.item_alarma, lista, false)
            val txtNombre = v.findViewById<TextView>(R.id.txtNombre)
            val txtHora = v.findViewById<TextView>(R.id.txtHora)
            val txtAmPm = v.findViewById<TextView>(R.id.txtAmPm)
            val txtDias = v.findViewById<TextView>(R.id.txtDias)
            val txtRetos = v.findViewById<TextView>(R.id.txtRetos)
            val sw = v.findViewById<SwitchMaterial>(R.id.swActiva)

            txtNombre.text = a.nombre
            txtNombre.visibility = if (a.nombre.isBlank()) View.GONE else View.VISIBLE
            if (formato24) {
                txtHora.text = "%d:%02d".format(a.hora, a.minuto)
                txtAmPm.text = ""
            } else {
                val h12 = if (a.hora % 12 == 0) 12 else a.hora % 12
                txtHora.text = "%d:%02d".format(h12, a.minuto)
                txtAmPm.text = if (a.hora < 12) "a.m." else "p.m."
            }
            txtDias.text = diasEnColor(a.dias)
            val retos = mutableListOf("🧻 objetos")
            if (a.virtual) retos += "🧭 virtual ${a.distancia} m"
            if (a.pasos) retos += "👣 ${a.numPasos} pasos"
            txtRetos.text = retos.joinToString("  ·  ")

            val alfa = if (a.activa) 1f else 0.45f
            txtHora.alpha = alfa; txtAmPm.alpha = alfa; txtNombre.alpha = alfa; txtDias.alpha = alfa

            sw.isChecked = a.activa
            sw.setOnCheckedChangeListener { _, activa ->
                a.activa = activa
                Prefs.guardarAlarma(this, a)
                val cuando = Programador.programar(this, a)
                if (cuando != null) avisarCuanto(cuando)
                pintarLista()
            }
            v.setOnClickListener {
                startActivity(Intent(this, EditarActivity::class.java).putExtra("id", a.id))
            }
            v.setOnLongClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("¿Borrar esta alarma?")
                    .setPositiveButton("Borrar") { _, _ ->
                        Programador.cancelar(this, a.id)
                        Prefs.borrarAlarma(this, a.id)
                        pintarLista()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }
            lista.addView(v)
        }

        val prox = Programador.proxima(this)
        txtProxima.text = if (prox == null) "Sin alarmas activas" else "Próxima alarma en " + faltan(prox.second)
    }

    /** "D L M X J V S" con los días elegidos en azul y negritas. */
    private fun diasEnColor(dias: Set<Int>): CharSequence {
        val sb = SpannableStringBuilder()
        val azul = ContextCompat.getColor(this, R.color.azul)
        val gris = ContextCompat.getColor(this, R.color.gris)
        for ((i, par) in Alarma.SEMANA.withIndex()) {
            if (i > 0) sb.append(" ")
            val ini = sb.length
            sb.append(par.second)
            val elegido = par.first in dias
            sb.setSpan(ForegroundColorSpan(if (elegido) azul else gris), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (elegido) sb.setSpan(StyleSpan(Typeface.BOLD), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (dias.isEmpty()) sb.append("   ·  una vez")
        return sb
    }

    private fun faltan(cuando: Long): String {
        val min = ((cuando - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        val d = min / 1440
        val h = (min % 1440) / 60
        val m = min % 60
        return when {
            d > 0 -> "$d d $h h"
            h > 0 -> "$h h $m min"
            else -> "$m min"
        }
    }

    private fun avisarCuanto(cuando: Long) {
        Toast.makeText(this, "Sonará en " + faltan(cuando), Toast.LENGTH_SHORT).show()
    }

    // ---------- Volumen ----------

    private fun prepararVolumen() {
        val txt = findViewById<TextView>(R.id.txtVolumen)
        val slider = findViewById<Slider>(R.id.sliderVolumen)
        val sw = findViewById<SwitchMaterial>(R.id.swForzar)
        slider.value = (Prefs.volumen(this) / 10 * 10).coerceIn(10, 100).toFloat()
        sw.isChecked = Prefs.forzarVolumen(this)
        fun actualizar() {
            val v = slider.value.toInt()
            txt.text = "🔊 Volumen de la alarma: $v%"
            Prefs.guardarVolumen(this, v, sw.isChecked)
        }
        slider.addOnChangeListener { _, _, _ -> actualizar() }
        sw.setOnCheckedChangeListener { _, _ -> actualizar() }
        actualizar()
    }

    // ---------- Permisos ----------

    private fun revisarPantallaCompleta() {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            btnPantallaCompleta.visibility = if (nm.canUseFullScreenIntent()) View.GONE else View.VISIBLE
        }
    }

    private fun pedirPermisosNecesarios() {
        val faltan = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            faltan += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            faltan += Manifest.permission.POST_NOTIFICATIONS
        }
        if (faltan.isNotEmpty()) pedirPermisos.launch(faltan.toTypedArray())
    }

    // ---------- Actualizaciones ----------

    private fun buscarActualizacion(manual: Boolean) {
        if (manual) Toast.makeText(this, "Buscando…", Toast.LENGTH_SHORT).show()
        Thread {
            val v = try { Actualizador.buscar() } catch (_: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    v != null && v.numero > Actualizador.versionActual(this) ->
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Nueva versión ${v.nombre}")
                            .setMessage("Hay una versión nueva de Despierta. ¿Descargarla e instalarla?")
                            .setPositiveButton("Actualizar") { _, _ -> descargarEInstalar(v) }
                            .setNegativeButton("Después", null)
                            .show()
                    manual && v != null -> Toast.makeText(this, "Ya tienes la última versión", Toast.LENGTH_SHORT).show()
                    manual -> Toast.makeText(this, "No se pudo revisar. ¿Hay internet?", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun descargarEInstalar(v: Actualizador.Version) {
        val barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            setPadding(60, 40, 60, 0)
        }
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle("Descargando ${v.nombre}…")
            .setView(barra)
            .setCancelable(false)
            .show()
        Thread {
            try {
                val archivo = Actualizador.descargar(this, v.url) { p ->
                    runOnUiThread {
                        barra.isIndeterminate = p < 0
                        if (p >= 0) barra.progress = p
                    }
                }
                runOnUiThread {
                    dialogo.dismiss()
                    if (!Actualizador.instalar(this, archivo)) archivoPendiente = archivo
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialogo.dismiss()
                    Toast.makeText(this, "Falló la descarga: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** APK descargado que espera a que se dé permiso de instalar. */
    private var archivoPendiente: java.io.File? = null

    private fun instalarPendiente() {
        val f = archivoPendiente ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) return
        archivoPendiente = null
        Actualizador.instalar(this, f)
    }
}
