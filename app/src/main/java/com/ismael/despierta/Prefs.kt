package com.ismael.despierta

import android.content.Context
import android.net.Uri

object Prefs {
    private const val ARCHIVO = "despierta"

    private fun sp(c: Context) =
        c.createDeviceProtectedStorageContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun hora(c: Context) = sp(c).getInt("hora", 7)
    fun minuto(c: Context) = sp(c).getInt("minuto", 0)
    fun activa(c: Context) = sp(c).getBoolean("activa", false)
    fun repetir(c: Context) = sp(c).getBoolean("repetir", true)
    fun tono(c: Context): Uri? = sp(c).getString("tono", null)?.let { Uri.parse(it) }
    fun objetos(c: Context): Set<String> =
        sp(c).getStringSet("objetos", null)?.toSet() ?: Objetos.predeterminados

    fun guardar(
        c: Context, hora: Int, minuto: Int, activa: Boolean, repetir: Boolean,
        tono: Uri?, objetos: Set<String>
    ) {
        sp(c).edit()
            .putInt("hora", hora)
            .putInt("minuto", minuto)
            .putBoolean("activa", activa)
            .putBoolean("repetir", repetir)
            .putString("tono", tono?.toString())
            .putStringSet("objetos", objetos)
            .apply()
    }

    fun desactivar(c: Context) {
        sp(c).edit().putBoolean("activa", false).apply()
    }
}
