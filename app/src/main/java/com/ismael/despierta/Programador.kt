package com.ismael.despierta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Programador {

    private fun intentAlarma(c: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, 1000 + id,
            Intent(c, AlarmReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intentMostrar(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 101, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Próximo momento (ms) en que debe sonar la alarma. */
    fun proximaVez(a: Alarma): Long {
        val ahora = System.currentTimeMillis()
        for (i in 0..7) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, a.hora)
                set(Calendar.MINUTE, a.minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > ahora && (a.dias.isEmpty() || cal.get(Calendar.DAY_OF_WEEK) in a.dias)) {
                return cal.timeInMillis
            }
        }
        return ahora + 24L * 3600_000L
    }

    fun cancelar(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(intentAlarma(c, id))
    }

    /** Programa (o cancela) una alarma. Devuelve la hora programada o null. */
    fun programar(c: Context, a: Alarma): Long? {
        cancelar(c, a.id)
        if (!a.activa) return null
        val am = c.getSystemService(AlarmManager::class.java)
        val cuando = proximaVez(a)
        val pi = intentAlarma(c, a.id)
        val exacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exacta) am.setAlarmClock(AlarmManager.AlarmClockInfo(cuando, intentMostrar(c)), pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        return cuando
    }

    fun programarTodas(c: Context) {
        // Alarma de la versión anterior (sin id)
        PendingIntent.getBroadcast(
            c, 100, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { c.getSystemService(AlarmManager::class.java).cancel(it) }
        Prefs.alarmas(c).forEach { programar(c, it) }
    }

    /** La próxima alarma activa y cuándo suena. */
    fun proxima(c: Context): Pair<Alarma, Long>? =
        Prefs.alarmas(c).filter { it.activa }.map { it to proximaVez(it) }.minByOrNull { it.second }
}
