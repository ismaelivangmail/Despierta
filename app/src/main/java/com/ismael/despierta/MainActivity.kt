package com.ismael.despierta

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.ar.core.ArCoreApk
import android.widget.ProgressBar
import java.util.Calendar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var selectorHora: TimePicker
    private lateinit var swActiva: SwitchMaterial
    private lateinit var txtRepeticion: TextView
    private lateinit var swRA: SwitchMaterial
    private lateinit var txtRA: TextView
    private lateinit var txtDistancia: TextView
    private lateinit var sliderDistancia: Slider
    private val chipsDias = mutableMapOf<Int, Chip>()
    private lateinit var txtTono: TextView
    private lateinit var txtEstado: TextView
    private lateinit var btnPantallaCompleta: MaterialButton

    private var tono: Uri? = null

    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val elegirTono =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val datos = r.data ?: return@registerForActivityResult
            if (r.resultCode == RESULT_OK) {
                tono = IntentCompat.getParcelableExtra(
                    datos, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                )
                mostrarTono()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectorHora = findViewById(R.id.selectorHora)
        swActiva = findViewById(R.id.swActiva)
        txtRepeticion = findViewById(R.id.txtRepeticion)
        txtTono = findViewById(R.id.txtTono)
        txtEstado = findViewById(R.id.txtEstado)
        btnPantallaCompleta = findViewById(R.id.btnPantallaCompleta)

        selectorHora.setIs24HourView(DateFormat.is24HourFormat(this))
        selectorHora.hour = Prefs.hora(this)
        selectorHora.minute = Prefs.minuto(this)
        swActiva.isChecked = Prefs.activa(this) || !Prefs.configurada(this)
        crearDias(Prefs.dias(this))
        prepararRA()
        findViewById<TextView>(R.id.txtVersion).text = "Versión ${Actualizador.nombreActual(this)}"
        findViewById<MaterialButton>(R.id.btnActualizar).setOnClickListener { buscarActualizacion(true) }
        if (savedInstanceState == null) buscarActualizacion(false)
        tono = Prefs.tono(this)
        mostrarTono()

        findViewById<MaterialButton>(R.id.btnTono).setOnClickListener {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de la alarma")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    tono ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            }
            elegirTono.launch(i)
        }

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { guardar() }

        findViewById<MaterialButton>(R.id.btnProbar).setOnClickListener {
            guardarPreferencias()
            Programador.programar(this)
            Prefs.borrarSorteo(this)
            ContextCompat.startForegroundService(this, Intent(this, AlarmService::class.java))
            startActivity(Intent(this, AlarmActivity::class.java))
        }

        btnPantallaCompleta.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName")))
            }
        }

        pedirPermisosNecesarios()
    }

    override fun onResume() {
        super.onResume()
        revisarPantallaCompleta()
        mostrarEstado()
        instalarPendiente()
    }

    private fun mostrarTono() {
        val nombre = tono?.let { RingtoneManager.getRingtone(this, it)?.getTitle(this) } ?: "predeterminado"
        txtTono.text = "Tono: $nombre"
    }

    private fun guardarPreferencias() {
        Prefs.guardarRA(this, swRA.isEnabled && swRA.isChecked, sliderDistancia.value.toInt())
        Prefs.guardar(
            this, selectorHora.hour, selectorHora.minute,
            swActiva.isChecked, diasElegidos(), tono
        )
    }

    private fun guardar() {
        guardarPreferencias()
        val cuando = Programador.programar(this)
        if (cuando != null) {
            val falta = cuando - System.currentTimeMillis()
            val h = falta / 3_600_000
            val m = (falta / 60_000) % 60
            Toast.makeText(this, "Sonará en $h h $m min", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Alarma desactivada", Toast.LENGTH_SHORT).show()
        }
        mostrarEstado()
    }

    private fun mostrarEstado() {
        txtEstado.text = if (Prefs.activa(this)) {
            val cuando = Programador.proximaVez(this)
            val f = SimpleDateFormat("EEEE HH:mm", Locale("es"))
            "Próxima alarma: ${f.format(Date(cuando))}"
        } else "Sin alarma programada"

        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                txtEstado.append("\n⚠️ Permite \"Alarmas y recordatorios\" en Ajustes para que suene a la hora exacta.")
            }
        }
    }

    private fun revisarPantallaCompleta() {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            btnPantallaCompleta.visibility =
                if (nm.canUseFullScreenIntent()) android.view.View.GONE else android.view.View.VISIBLE
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

    // ---------- Repetición ----------

    private val ordenDias = listOf(
        Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar", Calendar.WEDNESDAY to "Mié",
        Calendar.THURSDAY to "Jue", Calendar.FRIDAY to "Vie", Calendar.SATURDAY to "Sáb",
        Calendar.SUNDAY to "Dom"
    )

    private fun crearDias(elegidos: Set<Int>) {
        val grupo = findViewById<ChipGroup>(R.id.grupoDias)
        for ((dia, nombre) in ordenDias) {
            val chip = Chip(this).apply {
                text = nombre
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = dia in elegidos
                setOnCheckedChangeListener { _, _ -> mostrarRepeticion() }
            }
            chipsDias[dia] = chip
            grupo.addView(chip)
        }
        val atajos = findViewById<ChipGroup>(R.id.grupoAtajos)
        val opciones = listOf(
            "Una vez" to emptySet<Int>(),
            "Diario" to (1..7).toSet(),
            "Lun a Vie" to setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            "Fin de semana" to setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        for ((nombre, dias) in opciones) {
            atajos.addView(Chip(this).apply {
                text = nombre
                setOnClickListener { chipsDias.forEach { (d, c) -> c.isChecked = d in dias } }
            })
        }
        mostrarRepeticion()
    }

    private fun diasElegidos(): Set<Int> = chipsDias.filterValues { it.isChecked }.keys

    private fun mostrarRepeticion() {
        val d = diasElegidos()
        txtRepeticion.text = when {
            d.isEmpty() -> "Sonará una sola vez"
            d.size == 7 -> "Todos los días"
            d == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> "De lunes a viernes"
            d == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Fines de semana"
            else -> "Cada semana: " + ordenDias.filter { it.first in d }.joinToString(", ") { it.second }
        }
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

    // ---------- Realidad aumentada ----------

    private fun prepararRA() {
        swRA = findViewById(R.id.swRA)
        txtRA = findViewById(R.id.txtRA)
        txtDistancia = findViewById(R.id.txtDistancia)
        sliderDistancia = findViewById(R.id.sliderDistancia)
        swRA.isChecked = Prefs.raActivo(this)
        sliderDistancia.value = Prefs.raDistancia(this).toFloat()
        sliderDistancia.addOnChangeListener { _, _, _ -> mostrarDistancia() }
        swRA.setOnCheckedChangeListener { _, _ -> mostrarDistancia() }
        mostrarDistancia()
        revisarRA(0)
    }

    private fun mostrarDistancia() {
        val d = sliderDistancia.value.toInt()
        txtDistancia.text = "El despertador aparecerá a $d m de ti"
        val usable = swRA.isEnabled && swRA.isChecked
        sliderDistancia.isEnabled = usable
        txtDistancia.alpha = if (usable) 1f else 0.4f
    }

    private fun revisarRA(intento: Int) {
        val disp = try {
            ArCoreApk.getInstance().checkAvailability(this)
        } catch (_: Exception) {
            ArCoreApk.Availability.UNKNOWN_ERROR
        }
        if (disp.isTransient && intento < 30) {
            txtRA.text = "Revisando compatibilidad…"
            txtRA.postDelayed({ if (!isFinishing) revisarRA(intento + 1) }, 300)
            return
        }
        when {
            disp == ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                swRA.isEnabled = true
                txtRA.text = "✅ Tu teléfono es compatible. Al sonar podrás elegir buscar los objetos o el despertador virtual."
            }
            disp.isSupported -> {
                swRA.isEnabled = true
                txtRA.text = "✅ Tu teléfono es compatible. La primera vez te pedirá instalar \"Servicios de Google Play para RA\" (pruébalo antes con \"Probar alarma ahora\")."
            }
            disp == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                swRA.isEnabled = false
                swRA.isChecked = false
                txtRA.text = "❌ Tu teléfono no es compatible con realidad aumentada (ARCore). Solo podrás apagarla con objetos de la casa."
            }
            else -> {
                swRA.isEnabled = false
                swRA.isChecked = false
                txtRA.text = "⚠️ No se pudo comprobar la compatibilidad. Conéctate a internet y vuelve a abrir la app."
            }
        }
        mostrarDistancia()
    }
}
