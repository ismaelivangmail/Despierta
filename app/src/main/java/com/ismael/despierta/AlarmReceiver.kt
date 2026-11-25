package com.ismael.despierta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val a = Prefs.alarma(context, id)
        if (a != null) {
            if (a.dias.isEmpty()) {
                a.activa = false
                Prefs.guardarAlarma(context, a)
            } else {
                Programador.programar(context, a)
            }
        }
        if (AlarmService.sonando) return
        Alarmas.sonar(context, id)
    }
}
