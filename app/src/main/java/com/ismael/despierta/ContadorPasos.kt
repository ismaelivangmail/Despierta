package com.ismael.despierta

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detecta pasos con el acelerómetro: cada paso produce un "golpe" de aceleración.
 * (El sensor de pasos de Samsung tarda varios pasos en empezar a reportar, por eso no se usa.)
 */
class ContadorPasos(c: Context, private val alDarPaso: () -> Unit) : SensorEventListener {

    private val sm = c.getSystemService(SensorManager::class.java)
    private var base = SensorManager.GRAVITY_EARTH
    private var suave = SensorManager.GRAVITY_EARTH
    private var arriba = false
    private var ultimoPaso = 0L

    val disponible: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun iniciar() {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun detener() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val magnitud = sqrt(x * x + y * y + z * z)
        base = base * 0.98f + magnitud * 0.02f      // promedio lento (gravedad)
        suave = suave * 0.6f + magnitud * 0.4f      // señal suavizada
        val dif = suave - base
        val ahora = System.currentTimeMillis()
        if (!arriba && dif > 1.2f) {
            arriba = true
            if (ahora - ultimoPaso >= 280) {
                ultimoPaso = ahora
                alDarPaso()
            }
        } else if (arriba && dif < 0.2f) {
            arriba = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
