package com.ismael.despierta

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Alarma(
    val id: Int,
    var hora: Int = 7,
    var minuto: Int = 0,
    /** Días (Calendar.SUNDAY..SATURDAY). Vacío = una sola vez. */
    var dias: Set<Int> = emptySet(),
    var nombre: String = "",
    var activa: Boolean = true,
    var tono: String? = null,
    var vibrar: Boolean = true,
    /** Despertador virtual (giroscopio + pasos). */
    var virtual: Boolean = false,
    var distancia: Int = 3,
    /** Contar pasos. */
    var pasos: Boolean = false,
    var numPasos: Int = 30
) {
    fun aJson(): JSONObject = JSONObject().apply {
        put("id", id); put("hora", hora); put("minuto", minuto)
        put("dias", JSONArray(dias.sorted()))
        put("nombre", nombre); put("activa", activa)
        if (tono != null) put("tono", tono)
        put("vibrar", vibrar)
        put("virtual", virtual); put("distancia", distancia)
        put("pasos", pasos); put("numPasos", numPasos)
    }

    companion object {
        fun deJson(o: JSONObject): Alarma {
            val arr = o.optJSONArray("dias")
            val dias = if (arr == null) emptySet() else (0 until arr.length()).map { arr.getInt(it) }.toSet()
            return Alarma(
                id = o.getInt("id"),
                hora = o.optInt("hora", 7),
                minuto = o.optInt("minuto", 0),
                dias = dias,
                nombre = o.optString("nombre", ""),
                activa = o.optBoolean("activa", true),
                tono = if (o.has("tono")) o.getString("tono") else null,
                vibrar = o.optBoolean("vibrar", true),
                virtual = o.optBoolean("virtual", false),
                distancia = o.optInt("distancia", 3).coerceIn(1, 5),
                pasos = o.optBoolean("pasos", false),
                numPasos = o.optInt("numPasos", 30).coerceIn(10, 100)
            )
        }

        /** Orden de la semana como en el reloj de Samsung: D L M X J V S. */
        val SEMANA = listOf(
            Calendar.SUNDAY to "D", Calendar.MONDAY to "L", Calendar.TUESDAY to "M",
            Calendar.WEDNESDAY to "X", Calendar.THURSDAY to "J", Calendar.FRIDAY to "V",
            Calendar.SATURDAY to "S"
        )
        private val CORTOS = mapOf(
            Calendar.SUNDAY to "Dom", Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar",
            Calendar.WEDNESDAY to "Mié", Calendar.THURSDAY to "Jue", Calendar.FRIDAY to "Vie",
            Calendar.SATURDAY to "Sáb"
        )
        private val LUN_VIE = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

        fun resumenDias(dias: Set<Int>): String = when {
            dias.isEmpty() -> "Una sola vez"
            dias.size == 7 -> "Todos los días"
            dias == LUN_VIE -> "De lunes a viernes"
            dias == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Fines de semana"
            else -> "Cada " + SEMANA.filter { it.first in dias }.joinToString(", ") { CORTOS[it.first]!! }
        }
    }
}
