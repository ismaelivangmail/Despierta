package com.ismael.despierta

import android.content.Context
import org.json.JSONArray

object Prefs {
    private const val ARCHIVO = "despierta"

    private fun sp(c: Context) =
        c.createDeviceProtectedStorageContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    // ---------- Alarmas ----------

    fun alarmas(c: Context): MutableList<Alarma> {
        migrar(c)
        val texto = sp(c).getString("alarmas", "[]")!!
        val arr = try { JSONArray(texto) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull {
            try { Alarma.deJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
        }.sortedWith(compareBy({ it.hora }, { it.minuto })).toMutableList()
    }

    private fun guardarTodas(c: Context, lista: List<Alarma>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.aJson()) }
        sp(c).edit().putString("alarmas", arr.toString()).commit()
    }

    fun alarma(c: Context, id: Int): Alarma? = alarmas(c).firstOrNull { it.id == id }

    fun guardarAlarma(c: Context, a: Alarma) {
        val lista = alarmas(c)
        val i = lista.indexOfFirst { it.id == a.id }
        if (i >= 0) lista[i] = a else lista += a
        guardarTodas(c, lista)
    }

    fun borrarAlarma(c: Context, id: Int) {
        guardarTodas(c, alarmas(c).filter { it.id != id })
    }

    fun nuevoId(c: Context): Int = (alarmas(c).maxOfOrNull { it.id } ?: 0) + 1

    /** Convierte la alarma única de versiones anteriores en la primera de la lista. */
    private fun migrar(c: Context) {
        val p = sp(c)
        if (p.contains("alarmas") || !p.contains("hora")) return
        val dias = p.getString("dias", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: if (p.getBoolean("repetir", true)) (1..7).toSet() else emptySet()
        val a = Alarma(
            id = 1, hora = p.getInt("hora", 7), minuto = p.getInt("minuto", 0), dias = dias,
            activa = p.getBoolean("activa", false), tono = p.getString("tono", null)
        )
        val arr = JSONArray().put(a.aJson())
        p.edit().putString("alarmas", arr.toString()).commit()
    }

    // ---------- Volumen ----------

    /** Volumen de la alarma en % (10..100). */
    fun volumen(c: Context) = sp(c).getInt("volumen", 100).coerceIn(10, 100)
    /** Si está activo, no se puede bajar el volumen mientras suena. */
    fun forzarVolumen(c: Context) = sp(c).getBoolean("forzar_volumen", true)

    fun guardarVolumen(c: Context, volumen: Int, forzar: Boolean) {
        sp(c).edit().putInt("volumen", volumen).putBoolean("forzar_volumen", forzar).apply()
    }

    // ---------- Alarma sonando ----------

    fun sonandoId(c: Context) = sp(c).getInt("sonando_id", -1)
    fun guardarSonandoId(c: Context, id: Int) { sp(c).edit().putInt("sonando_id", id).commit() }

    /** Objetos sorteados para la alarma que está sonando. */
    fun sorteo(c: Context): List<String> =
        sp(c).getString("sorteo", "")!!.split(",").filter { it.isNotBlank() }

    fun guardarSorteo(c: Context, ids: List<String>) {
        sp(c).edit().putString("sorteo", ids.joinToString(",")).commit()
    }

    fun borrarSorteo(c: Context) {
        sp(c).edit().remove("sorteo").commit()
    }
}
