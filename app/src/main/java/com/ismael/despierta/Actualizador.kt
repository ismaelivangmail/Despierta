package com.ismael.despierta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Busca nuevas versiones en los Releases de GitHub, las descarga y las instala. */
object Actualizador {
    private const val API = "https://api.github.com/repos/ismaelivangmail/Despierta/releases/latest"

    data class Version(val numero: Int, val nombre: String, val url: String)

    fun versionActual(c: Context): Int =
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0)).toInt()

    fun nombreActual(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"

    /** Llamar fuera del hilo principal. Devuelve la última versión publicada o null. */
    fun buscar(): Version? {
        val con = URL(API).openConnection() as HttpURLConnection
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (con.responseCode != 200) return null
            val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val numero = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Version(numero, tag, a.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            con.disconnect()
        }
    }

    /** Llamar fuera del hilo principal. progreso recibe 0..100 (o -1 si no se conoce el tamaño). */
    fun descargar(c: Context, url: String, progreso: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "apk").apply { mkdirs() }
        val archivo = File(dir, "Despierta.apk")
        val con = URL(url).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true
        con.connectTimeout = 15_000
        con.readTimeout = 30_000
        try {
            if (con.responseCode != 200) throw Exception("HTTP ${con.responseCode}")
            val total = con.contentLengthLong
            var leidos = 0L
            var ultimo = -2
            con.inputStream.use { entrada ->
                archivo.outputStream().use { salida ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        salida.write(buf, 0, n)
                        leidos += n
                        val p = if (total > 0) ((leidos * 100) / total).toInt() else -1
                        if (p != ultimo) { ultimo = p; progreso(p) }
                    }
                }
            }
        } finally {
            con.disconnect()
        }
        return archivo
    }

    /** Abre el instalador de Android. Devuelve false si primero hay que dar permiso. */
    fun instalar(a: Activity, archivo: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "Activa \"Permitir de esta fuente\" y vuelve a la app", Toast.LENGTH_LONG).show()
            a.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))
            )
            return false
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.archivos", archivo)
        a.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
