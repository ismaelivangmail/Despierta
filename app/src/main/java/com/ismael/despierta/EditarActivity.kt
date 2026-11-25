package com.ismael.despierta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/** Crear o editar una alarma (estilo reloj de Samsung). */
class EditarActivity : AppCompatActivity() {

    private lateinit var alarma: Alarma
    private var esNueva = true
    private val dias = mutableSetOf<Int>()
    private val circulos = mutableMapOf<Int, TextView>()

    private lateinit var selectorHora: TimePicker
    private lateinit var txtResumen: TextView
    private lateinit var editNombre: EditText
    private lateinit var txtTono: TextView
    private lateinit var swVibrar: SwitchMaterial
    private lateinit var swVirtual: SwitchMaterial
    private lateinit var swPasos: SwitchMaterial
    private lateinit var sliderDistancia: Slider
    private lateinit var sliderPasos: Slider
    private lateinit var txtDistancia: TextView
    private lateinit var txtNumPasos: TextView
    private var tono: String? = null

    private val elegirTono =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val datos = r.data ?: return@registerForActivityResult
            if (r.resultCode == RESULT_OK) {
                tono = IntentCompat.getParcelableExtra(datos, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.toString()
                mostrarTono()
            }
        }

    private val pedirPermisoPasos =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar)

        val id = intent.getIntExtra("id", -1)
        val existente = if (id >= 0) Prefs.alarma(this, id) else null
        esNueva = existente == null
        alarma = existente ?: Alarma(id = Prefs.nuevoId(this), dias = setOf(2, 3, 4, 5, 6))
        dias += alarma.dias
        tono = alarma.tono

        selectorHora = findViewById(R.id.selectorHora)
        txtResumen = findViewById(R.id.txtResumen)
        editNombre = findViewById(R.id.editNombre)
        txtTono = findViewById(R.id.txtTono)
        swVibrar = findViewById(R.id.swVibrar)
        swVirtual = findViewById(R.id.swVirtual)
        swPasos = findViewById(R.id.swPasos)
        sliderDistancia = findViewById(R.id.sliderDistancia)
        sliderPasos = findViewById(R.id.sliderPasos)
        txtDistancia = findViewById(R.id.txtDistancia)
        txtNumPasos = findViewById(R.id.txtNumPasos)

        selectorHora.setIs24HourView(DateFormat.is24HourFormat(this))
        selectorHora.hour = alarma.hora
        selectorHora.minute = alarma.minuto
        editNombre.setText(alarma.nombre)
        swVibrar.isChecked = alarma.vibrar
        swVirtual.isChecked = alarma.virtual
        swPasos.isChecked = alarma.pasos
        sliderDistancia.value = alarma.distancia.toFloat()
        sliderPasos.value = (alarma.numPasos / 5 * 5).coerceIn(10, 100).toFloat()

        crearCirculos()
        mostrarTono()
        mostrarRetos()

        swVirtual.setOnCheckedChangeListener { _, on -> if (on) pedirPermisoDePasos(); mostrarRetos() }
        swPasos.setOnCheckedChangeListener { _, on -> if (on) pedirPermisoDePasos(); mostrarRetos() }
        sliderDistancia.addOnChangeListener { _, _, _ -> mostrarRetos() }
        sliderPasos.addOnChangeListener { _, _, _ -> mostrarRetos() }

        findViewById<View>(R.id.filaTono).setOnClickListener {
            elegirTono.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de la alarma")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    tono?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            })
        }
        findViewById<View>(R.id.btnCancelar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnGuardar).setOnClickListener {
            guardar()
            val cuando = Programador.programar(this, alarma)
            if (cuando != null) Toast.makeText(this, "Alarma guardada", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<MaterialButton>(R.id.btnProbar).setOnClickListener {
            guardar()
            Programador.programar(this, alarma)
            esNueva = false
            Alarmas.sonar(this, alarma.id)
            startActivity(Intent(this, AlarmActivity::class.java))
        }
        val btnEliminar = findViewById<MaterialButton>(R.id.btnEliminar)
        btnEliminar.visibility = if (esNueva) View.INVISIBLE else View.VISIBLE
        btnEliminar.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Borrar esta alarma?")
                .setPositiveButton("Borrar") { _, _ ->
                    Programador.cancelar(this, alarma.id)
                    Prefs.borrarAlarma(this, alarma.id)
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun guardar() {
        alarma.hora = selectorHora.hour
        alarma.minuto = selectorHora.minute
        alarma.dias = dias.toSet()
        alarma.nombre = editNombre.text.toString().trim()
        alarma.tono = tono
        alarma.vibrar = swVibrar.isChecked
        alarma.virtual = swVirtual.isChecked
        alarma.distancia = sliderDistancia.value.toInt()
        alarma.pasos = swPasos.isChecked
        alarma.numPasos = sliderPasos.value.toInt()
        alarma.activa = true
        Prefs.guardarAlarma(this, alarma)
    }

    // ---------- Días en círculos ----------

    private fun crearCirculos() {
        val fila = findViewById<LinearLayout>(R.id.filaDias)
        val tam = (44 * resources.displayMetrics.density).toInt()
        for ((dia, letra) in Alarma.SEMANA) {
            val celda = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, tam, 1f)
            }
            val circulo = TextView(this).apply {
                text = letra
                textSize = 17f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(tam, tam, Gravity.CENTER)
                setOnClickListener {
                    if (dia in dias) dias.remove(dia) else dias.add(dia)
                    pintarDias()
                }
            }
            celda.addView(circulo)
            fila.addView(celda)
            circulos[dia] = circulo
        }
        pintarDias()
    }

    private fun pintarDias() {
        val azul = ContextCompat.getColor(this, R.color.azul)
        for ((dia, tv) in circulos) {
            val elegido = dia in dias
            tv.background = if (elegido) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(azul)
            } else null
            tv.setTextColor(
                when {
                    elegido -> 0xFF101010.toInt()
                    dia == java.util.Calendar.SUNDAY -> ContextCompat.getColor(this, R.color.rojo_domingo)
                    else -> 0xFFFFFFFF.toInt()
                }
            )
        }
        txtResumen.text = Alarma.resumenDias(dias)
    }

    // ---------- Otros ----------

    private fun mostrarTono() {
        txtTono.text = tono?.let { RingtoneManager.getRingtone(this, Uri.parse(it))?.getTitle(this) }
            ?: "Predeterminado"
    }

    private fun mostrarRetos() {
        val d = sliderDistancia.value.toInt()
        txtDistancia.text = "Aparece a $d m: gira hasta verlo y camina hacia él"
        txtNumPasos.text = "Camina ${sliderPasos.value.toInt()} pasos"
        sliderDistancia.isEnabled = swVirtual.isChecked
        sliderPasos.isEnabled = swPasos.isChecked
        txtDistancia.alpha = if (swVirtual.isChecked) 1f else 0.4f
        txtNumPasos.alpha = if (swPasos.isChecked) 1f else 0.4f
    }

    private fun pedirPermisoDePasos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            pedirPermisoPasos.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
}
