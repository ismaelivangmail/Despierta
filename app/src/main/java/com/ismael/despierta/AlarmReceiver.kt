package com.ismael.despierta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Programar la siguiente (mañana) o desactivar si no se repite
        if (Prefs.dias(context).isNotEmpty()) {
            Programador.programar(context)
        } else {
            Prefs.desactivar(context)
        }
        Prefs.borrarSorteo(context)
        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
    }
}
