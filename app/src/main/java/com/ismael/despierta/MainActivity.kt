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
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var selectorHora: TimePicker
    private lateinit var swActiva: SwitchMaterial
    private lateinit var swRepetir: SwitchMaterial
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
        swRepetir = findViewById(R.id.swRepetir)
        txtTono = findViewById(R.id.txtTono)
        txtEstado = findViewById(R.id.txtEstado)
        btnPantallaCompleta = findViewById(R.id.btnPantallaCompleta)

        selectorHora.setIs24HourView(DateFormat.is24HourFormat(this))
        selectorHora.hour = Prefs.hora(this)
        selectorHora.minute = Prefs.minuto(this)
        swActiva.isChecked = Prefs.activa(this) || !Prefs.configurada(this)
        swRepetir.isChecked = Prefs.repetir(this)
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
    }

    private fun mostrarTono() {
        val nombre = tono?.let { RingtoneManager.getRingtone(this, it)?.getTitle(this) } ?: "predeterminado"
        txtTono.text = "Tono: $nombre"
    }

    private fun guardarPreferencias() {
        Prefs.guardar(
            this, selectorHora.hour, selectorHora.minute,
            swActiva.isChecked, swRepetir.isChecked, tono
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
}
