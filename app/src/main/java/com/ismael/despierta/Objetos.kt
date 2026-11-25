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
        Objeto("inodoro", "Inodoro (tapa)", "🚽", "baño", listOf("toilet seat")),
        Objeto("jabon", "Despachador de jabón", "🧴", "baño", listOf("soap dispenser")),
        Objeto("toalla", "Toalla", "🛁", "baño", listOf("bath towel")),
        Objeto("cortina", "Cortina de baño", "🚿", "baño", listOf("shower curtain")),
        Objeto("tina", "Tina de baño", "🛀", "baño", listOf("bathtub")),
        Objeto("destapacanos", "Destapacaños", "🪠", "baño", listOf("plunger")),

        // Cocina
        Objeto("refri", "Refrigerador", "🧊", "cocina", listOf("refrigerator")),
        Objeto("microondas", "Microondas", "📦", "cocina", listOf("microwave")),
        Objeto("estufa", "Estufa", "🔥", "cocina", listOf("stove")),
        Objeto("tostador", "Tostador", "🍞", "cocina", listOf("toaster")),
        Objeto("sarten", "Sartén", "🍳", "cocina", listOf("frying pan", "wok")),
        Objeto("cafetera", "Cafetera", "☕", "cocina", listOf("coffeepot", "espresso maker")),
        Objeto("tetera", "Tetera", "🫖", "cocina", listOf("teapot")),
        Objeto("taza", "Taza", "🍵", "cocina", listOf("coffee mug", "cup")),
        Objeto("tazon", "Tazón / bowl", "🥣", "cocina", listOf("mixing bowl", "soup bowl")),
        Objeto("jarra", "Jarra", "🫗", "cocina", listOf("pitcher", "water jug")),
        Objeto("botella", "Botella", "🍶", "cocina", listOf("water bottle", "pop bottle", "beer bottle", "wine bottle")),
        Objeto("cuchara_madera", "Cuchara de madera", "🥄", "cocina", listOf("wooden spoon")),
        Objeto("espatula", "Espátula", "🍳", "cocina", listOf("spatula")),
        Objeto("cucharon", "Cucharón", "🥄", "cocina", listOf("ladle")),
        Objeto("abrelatas", "Abrelatas", "🥫", "cocina", listOf("can opener")),
        Objeto("salero", "Salero", "🧂", "cocina", listOf("saltshaker")),
        Objeto("toalla_papel", "Rollo de toalla de papel", "🧻", "cocina", listOf("paper towel")),
        Objeto("escurridor", "Escurridor de platos", "🍽️", "cocina", listOf("plate rack")),
        Objeto("platano", "Plátano", "🍌", "cocina", listOf("banana")),
        Objeto("naranja", "Naranja", "🍊", "cocina", listOf("orange")),

        // Limpieza / lavado
        Objeto("lavadora", "Lavadora", "🫧", "limpieza", listOf("washer")),
        Objeto("plancha", "Plancha", "👕", "limpieza", listOf("iron")),
        Objeto("aspiradora", "Aspiradora", "🌀", "limpieza", listOf("vacuum")),
        Objeto("escoba", "Escoba", "🧹", "limpieza", listOf("broom")),
        Objeto("trapeador", "Trapeador", "🧽", "limpieza", listOf("swab")),
        Objeto("cubeta", "Cubeta", "🪣", "limpieza", listOf("bucket")),
        Objeto("basura", "Bote de basura", "🗑️", "limpieza", listOf("ashcan")),

        // Sala / comedor / entrada
        Objeto("sillon", "Sillón", "🛋️", "sala", listOf("studio couch")),
        Objeto("mesa", "Mesa del comedor", "🪑", "sala", listOf("dining table")),
        Objeto("librero", "Librero", "📚", "sala", listOf("bookcase")),
        Objeto("maceta", "Maceta", "🪴", "sala", listOf("pot")),
        Objeto("florero", "Florero", "💐", "sala", listOf("vase")),
        Objeto("tapete", "Tapete de entrada", "🚪", "sala", listOf("doormat")),
        Objeto("vela", "Vela", "🕯️", "sala", listOf("candle")),
        Objeto("paraguas", "Paraguas", "☂️", "sala", listOf("umbrella"))
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
