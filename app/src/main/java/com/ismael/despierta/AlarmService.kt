package com.ismael.despierta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    companion object {
        const val CANAL = "alarma_despierta"
        const val ID_NOTIF = 7
        /** Por seguridad, la alarma se calla sola después de este tiempo. */
        const val LIMITE_MS = 30L * 60L * 1000L
        @Volatile var sonando = false
    }

    private var reproductor: MediaPlayer? = null
    private var alarma: Alarma? = null
    private var vibrador: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var volumenOriginal = -1
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val primeraVez = !sonando
        if (primeraVez) {
            val id = intent?.getIntExtra("id", -1)?.takeIf { it >= 0 } ?: Prefs.sonandoId(this)
            Prefs.guardarSonandoId(this, id)
            alarma = Prefs.alarma(this, id)
        }
        crearCanal()
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, ID_NOTIF, crearNotificacion(), tipo)

        if (primeraVez) {
            sonando = true
            Objetos.sorteoActual(this)
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "despierta:alarma").apply {
                acquire(LIMITE_MS + 60_000L)
            }
            sonar()
            if (alarma?.vibrar != false) vibrar()
            handler.postDelayed({ stopSelf() }, LIMITE_MS)
            abrirPantallaAlarma()
        }
        return START_STICKY
    }

    private fun abrirPantallaAlarma() {
        try {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // En algunos Android no se permite abrir pantallas desde segundo plano;
            // la notificación de pantalla completa se encarga.
        }
    }

    private fun sonar() {
        val am = getSystemService(AudioManager::class.java)
        volumenOriginal = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) } catch (_: Exception) {}

        val candidatos = listOfNotNull(
            alarma?.tono?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidatos) {
            if (intentarReproducir(uri)) return
        }
    }

    private fun intentarReproducir(uri: Uri): Boolean = try {
        reproductor = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
        true
    } catch (_: Exception) {
        reproductor?.release()
        reproductor = null
        false
    }

    @Suppress("DEPRECATION")
    private fun vibrar() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrador = v
        val patron = longArrayOf(0, 800, 600)
        v.vibrate(
            VibrationEffect.createWaveform(patron, 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
    }

    private fun crearCanal() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) == null) {
            val canal = NotificationChannel(CANAL, "Alarma", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarma sonando"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(): Notification {
        val abrir = PendingIntent.getActivity(
            this, 200,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_alarma)
            .setContentTitle("⏰ " + (alarma?.nombre?.takeIf { it.isNotBlank() } ?: "¡Despierta!"))
            .setContentText("Toca aquí y muestra uno de tus objetos a la cámara para apagarla")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            .build()
    }

    override fun onDestroy() {
        sonando = false
        Prefs.borrarSorteo(this)
        handler.removeCallbacksAndMessages(null)
        try { reproductor?.stop() } catch (_: Exception) {}
        reproductor?.release()
        reproductor = null
        vibrador?.cancel()
        if (volumenOriginal >= 0) {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volumenOriginal, 0)
            } catch (_: Exception) {}
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
