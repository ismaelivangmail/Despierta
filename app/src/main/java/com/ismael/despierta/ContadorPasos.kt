package com.ismael.despierta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Detecta pasos. Usa el sensor de pasos del teléfono si existe y hay permiso;
 * si no, los detecta con el acelerómetro.
 */
class ContadorPasos(private val c: Context, private val alDarPaso: () -> Unit) : SensorEventListener {

    private val sm = c.getSystemService(SensorManager::class.java)
    private var filtrado = SensorManager.GRAVITY_EARTH
    private var arriba = false
    private var ultimoPaso = 0L

    private fun permisoPasos(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun iniciar() {
        val detector = if (permisoPasos()) sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
        if (detector != null) {
            sm.registerListener(this, detector, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun detener() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            alDarPaso()
            return
        }
        // Acelerómetro: un paso = pico de aceleración (con filtro y tiempo mínimo entre pasos)
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val magnitud = sqrt(x * x + y * y + z * z)
        filtrado = filtrado * 0.8f + magnitud * 0.2f
        val ahora = System.currentTimeMillis()
        if (!arriba && filtrado > 11.2f) {
            arriba = true
            if (ahora - ultimoPaso > 300) {
                ultimoPaso = ahora
                alDarPaso()
            }
        } else if (arriba && filtrado < 9.8f) {
            arriba = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
