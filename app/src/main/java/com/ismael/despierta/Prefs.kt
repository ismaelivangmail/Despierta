package com.ismael.despierta

import android.content.Context
import android.net.Uri

object Prefs {
    private const val ARCHIVO = "despierta"

    private fun sp(c: Context) =
        c.createDeviceProtectedStorageContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun configurada(c: Context) = sp(c).contains("hora")
    fun hora(c: Context) = sp(c).getInt("hora", 7)
    fun minuto(c: Context) = sp(c).getInt("minuto", 0)
    fun activa(c: Context) = sp(c).getBoolean("activa", false)
    fun repetir(c: Context) = sp(c).getBoolean("repetir", true)
    fun tono(c: Context): Uri? = sp(c).getString("tono", null)?.let { Uri.parse(it) }

    fun guardar(c: Context, hora: Int, minuto: Int, activa: Boolean, repetir: Boolean, tono: Uri?) {
        sp(c).edit()
            .putInt("hora", hora)
            .putInt("minuto", minuto)
            .putBoolean("activa", activa)
            .putBoolean("repetir", repetir)
            .putString("tono", tono?.toString())
            .apply()
    }

    fun desactivar(c: Context) {
        sp(c).edit().putBoolean("activa", false).apply()
    }

    // Objetos sorteados para la alarma que está sonando (se guardan para que no cambien
    // si se cierra y vuelve a abrir la pantalla de la alarma).
    fun sorteo(c: Context): List<String> =
        sp(c).getString("sorteo", "")!!.split(",").filter { it.isNotBlank() }

    fun guardarSorteo(c: Context, ids: List<String>) {
        sp(c).edit().putString("sorteo", ids.joinToString(",")).commit()
    }

    fun borrarSorteo(c: Context) {
        sp(c).edit().remove("sorteo").commit()
    }
}
