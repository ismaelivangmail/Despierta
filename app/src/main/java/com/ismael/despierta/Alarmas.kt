package com.ismael.despierta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Utilidades comunes para hacer sonar y apagar la alarma. */
object Alarmas {

    fun sonar(c: Context, id: Int) {
        Prefs.guardarSonandoId(c, id)
        Prefs.borrarSorteo(c)
        ContextCompat.startForegroundService(c, Intent(c, AlarmService::class.java).putExtra("id", id))
    }

    fun sonando(c: Context): Alarma? = Prefs.alarma(c, Prefs.sonandoId(c))

    fun mostrarSobreBloqueo(a: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            a.setShowWhenLocked(true)
            a.setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            a.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun apagar(a: Activity, emoji: String) {
        a.stopService(Intent(a, AlarmService::class.java))
        Toast.makeText(a, "¡Buenos días! $emoji", Toast.LENGTH_LONG).show()
        a.finish()
    }
}
