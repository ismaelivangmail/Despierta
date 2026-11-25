package com.ismael.despierta

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Reto: caminar cierta cantidad de pasos para apagar la alarma. */
class PasosActivity : AppCompatActivity() {

    private lateinit var contador: ContadorPasos
    private var meta = 30
    private var pasos = 0
    private var terminado = false
    private lateinit var txt: TextView
    private lateinit var barra: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarmas.mostrarSobreBloqueo(this)
        setContentView(R.layout.activity_pasos)
        txt = findViewById(R.id.txtPasos)
        barra = findViewById(R.id.barraPasos)
        meta = Alarmas.sonando(this)?.numPasos ?: 30
        pasos = savedInstanceState?.getInt("pasos") ?: 0
        mostrar()
        findViewById<MaterialButton>(R.id.btnVolver).setOnClickListener { finish() }

        contador = ContadorPasos(this) {
            runOnUiThread {
                if (terminado) return@runOnUiThread
                pasos++
                mostrar()
                if (pasos >= meta) {
                    terminado = true
                    Alarmas.apagar(this, "👣")
                }
            }
        }
        vigilar()
    }

    private fun mostrar() {
        txt.text = "$pasos / $meta"
        barra.progress = (pasos * 100 / meta.coerceAtLeast(1)).coerceAtMost(100)
    }

    private fun vigilar() {
        txt.postDelayed({
            if (!AlarmService.sonando && !terminado) finish() else if (!terminado) vigilar()
        }, 2000)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pasos", pasos)
    }

    override fun onResume() { super.onResume(); contador.iniciar() }
    override fun onPause() { super.onPause(); contador.detener() }
}
