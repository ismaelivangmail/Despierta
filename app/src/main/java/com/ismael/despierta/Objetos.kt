package com.ismael.despierta

import android.content.Context

/**
 * Objetos de la casa que pueden pedirse para apagar la alarma.
 * "etiquetas" son las clases del modelo MobileNet (ImageNet) que cuentan como ese objeto.
 * A propósito NO hay objetos de recámara, para obligar a salir del cuarto.
 */
data class Objeto(
    val id: String,
    val nombre: String,
    val emoji: String,
    val zona: String,
    val etiquetas: List<String>
)

object Objetos {
    const val CANTIDAD = 7
    private const val MAX_POR_ZONA = 2

    val todos = listOf(
        // Baño
        Objeto("papel_bano", "Papel de baño", "🧻", "baño", listOf("toilet tissue")),
        Objeto("lavabo", "Lavabo", "🚰", "baño", listOf("washbasin")),
        Objeto("inodoro", "Taza del baño (con la tapa abierta)", "🚽", "baño", listOf("toilet seat")),
        Objeto("toalla", "Toalla", "🛁", "baño", listOf("bath towel")),

        // Cocina
        Objeto("refri", "Refrigerador", "🧊", "cocina", listOf("refrigerator")),
        Objeto("microondas", "Microondas", "📦", "cocina", listOf("microwave")),
        Objeto("estufa", "Estufa", "🔥", "cocina", listOf("stove")),
        Objeto("sarten", "Sartén", "🍳", "cocina", listOf("frying pan", "wok")),
        Objeto("taza", "Taza", "☕", "cocina", listOf("coffee mug", "cup")),
        Objeto("tazon", "Plato hondo / tazón", "🥣", "cocina", listOf("mixing bowl", "soup bowl")),
        Objeto("garrafon", "Garrafón o jarra de agua", "💧", "cocina", listOf("water jug", "pitcher")),
        Objeto("botella", "Botella (agua o refresco)", "🍶", "cocina", listOf("water bottle", "pop bottle", "beer bottle")),
        Objeto("platano", "Plátano", "🍌", "cocina", listOf("banana")),

        // Limpieza
        Objeto("escoba", "Escoba", "🧹", "limpieza", listOf("broom")),
        Objeto("trapeador", "Trapeador", "🧽", "limpieza", listOf("swab")),
        Objeto("cubeta", "Cubeta", "🪣", "limpieza", listOf("bucket")),
        Objeto("basura", "Bote de basura", "🗑️", "limpieza", listOf("ashcan")),
        Objeto("lavadora", "Lavadora", "🫧", "limpieza", listOf("washer")),
        Objeto("plancha", "Plancha", "👕", "limpieza", listOf("iron")),

        // Sala / comedor
        Objeto("sillon", "Sillón", "🛋️", "sala", listOf("studio couch")),
        Objeto("mesa", "Mesa del comedor", "🪑", "sala", listOf("dining table")),
        Objeto("maceta", "Maceta", "🪴", "sala", listOf("pot"))
    )

    fun porId(id: String): Objeto? = todos.firstOrNull { it.id == id }

    /** Elige 7 objetos al azar, máximo 2 por zona, para que estén repartidos por la casa. */
    fun sortear(): List<Objeto> {
        val porZona = mutableMapOf<String, Int>()
        val elegidos = mutableListOf<Objeto>()
        for (o in todos.shuffled()) {
            val n = porZona[o.zona] ?: 0
            if (n < MAX_POR_ZONA) {
                elegidos += o
                porZona[o.zona] = n + 1
                if (elegidos.size == CANTIDAD) break
            }
        }
        return elegidos
    }

    /** Los objetos de la alarma que está sonando; si aún no hay sorteo, lo hace y lo guarda. */
    fun sorteoActual(c: Context): List<Objeto> {
        val guardados = Prefs.sorteo(c).mapNotNull { porId(it) }
        if (guardados.isNotEmpty()) return guardados
        val nuevos = sortear()
        Prefs.guardarSorteo(c, nuevos.map { it.id })
        return nuevos
    }
}
