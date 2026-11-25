package com.ismael.despierta

/**
 * Objetos que se pueden usar para apagar la alarma.
 * "etiquetas" son las clases del modelo MobileNet (ImageNet) que cuentan como ese objeto.
 */
data class Objeto(
    val id: String,
    val nombre: String,
    val emoji: String,
    val etiquetas: List<String>
)

object Objetos {
    val todos = listOf(
        Objeto("papel_bano", "Papel de baño", "🧻", listOf("toilet tissue")),
        Objeto("taza", "Taza", "☕", listOf("coffee mug", "cup")),
        Objeto("botella", "Botella", "🍶", listOf("water bottle", "pop bottle", "beer bottle", "wine bottle")),
        Objeto("jabon", "Despachador de jabón", "🧴", listOf("soap dispenser")),
        Objeto("lavabo", "Lavabo", "🚰", listOf("washbasin")),
        Objeto("inodoro", "Inodoro (tapa)", "🚽", listOf("toilet seat")),
        Objeto("toalla", "Toalla", "🛁", listOf("bath towel")),
        Objeto("refri", "Refrigerador", "🧊", listOf("refrigerator")),
        Objeto("microondas", "Microondas", "📦", listOf("microwave")),
        Objeto("tostador", "Tostador", "🍞", listOf("toaster")),
        Objeto("platano", "Plátano", "🍌", listOf("banana")),
        Objeto("tenis", "Tenis / zapato", "👟", listOf("running shoe", "loafer", "sandal", "clog")),
        Objeto("calcetin", "Calcetín", "🧦", listOf("sock")),
        Objeto("control", "Control remoto", "📺", listOf("remote control")),
        Objeto("teclado", "Teclado", "⌨️", listOf("computer keyboard", "typewriter keyboard")),
        Objeto("mouse", "Mouse", "🖱️", listOf("mouse")),
        Objeto("laptop", "Laptop", "💻", listOf("laptop", "notebook")),
        Objeto("mochila", "Mochila", "🎒", listOf("backpack")),
        Objeto("paraguas", "Paraguas", "☂️", listOf("umbrella")),
        Objeto("reloj", "Reloj de pared", "🕰️", listOf("wall clock", "analog clock"))
    )

    val predeterminados = setOf("papel_bano", "taza", "botella")

    fun porId(id: String): Objeto? = todos.firstOrNull { it.id == id }
}
