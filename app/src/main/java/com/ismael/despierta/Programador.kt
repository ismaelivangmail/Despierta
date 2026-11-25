package com.ismael.despierta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Programador {

    private fun intentAlarma(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 100, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intentMostrar(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 101, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Próximo momento (en ms) en que debe sonar la alarma configurada. */
    fun proximaVez(c: Context): Long {
        val ahora = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Prefs.hora(c))
            set(Calendar.MINUTE, Prefs.minuto(c))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!cal.after(ahora)) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Programa (o cancela) la alarma según lo guardado. Devuelve la hora programada o null. */
    fun programar(c: Context): Long? {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = intentAlarma(c)
        am.cancel(pi)
        if (!Prefs.activa(c)) return null

        val cuando = proximaVez(c)
        val exactaPermitida = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactaPermitida) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(cuando, intentMostrar(c)), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        }
        return cuando
    }
}
