package com.mohdrozaimi.cinepro

import org.json.JSONObject

/** Gaya warna. id mesti sama dengan nombor dalam shader (CineShader). */
enum class ColorLook(val id: Int, val label: String) {
    NATURAL(0, "Asli"),
    ARRI(1, "ARRI"),
    CANON(2, "Canon"),
    HARCOURT(3, "Harcourt"),
    TEAL_ORANGE(4, "Teal & Oren"),
    KODAK(5, "Kodak Print"),
}

/** Kesan lensa / diffusion. id mesti sama dengan shader. */
enum class LensFx(val id: Int, val label: String) {
    NONE(0, "Tiada"),
    BLACK_MIST(1, "Black Mist"),
    TIFFEN(2, "Tiffen Pro-Mist"),
    CINEBLOOM(3, "CineBloom"),
}

/** Semua tetapan filter yang dihantar ke GPU setiap bingkai. */
data class FilterParams(
    val look: ColorLook = ColorLook.ARRI,
    val lookAmount: Float = 0.8f,
    val lens: LensFx = LensFx.BLACK_MIST,
    val lensAmount: Float = 0.5f,
    val letterbox: Boolean = true,
    val grain: Boolean = true,
    val grainAmount: Float = 0.3f,
    val halation: Boolean = true,
    val vignette: Boolean = true,
)

data class Preset(val name: String, val params: FilterParams, val builtIn: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("look", params.look.name)
        put("lookAmount", params.lookAmount.toDouble())
        put("lens", params.lens.name)
        put("lensAmount", params.lensAmount.toDouble())
        put("letterbox", params.letterbox)
        put("grain", params.grain)
        put("grainAmount", params.grainAmount.toDouble())
        put("halation", params.halation)
        put("vignette", params.vignette)
    }

    companion object {
        fun fromJson(o: JSONObject): Preset? = try {
            Preset(
                o.getString("name"),
                FilterParams(
                    look = ColorLook.valueOf(o.getString("look")),
                    lookAmount = o.getDouble("lookAmount").toFloat(),
                    lens = LensFx.valueOf(o.getString("lens")),
                    lensAmount = o.getDouble("lensAmount").toFloat(),
                    letterbox = o.getBoolean("letterbox"),
                    grain = o.getBoolean("grain"),
                    grainAmount = o.optDouble("grainAmount", 0.3).toFloat(),
                    halation = o.getBoolean("halation"),
                    vignette = o.getBoolean("vignette"),
                )
            )
        } catch (e: Exception) {
            null
        }

        val BUILT_IN = listOf(
            Preset("Drama Malam", FilterParams(ColorLook.ARRI, 0.85f, LensFx.BLACK_MIST, 0.6f, true, true, 0.3f, true, true), true),
            Preset("Iklan Raya", FilterParams(ColorLook.CANON, 0.75f, LensFx.CINEBLOOM, 0.4f, true, true, 0.15f, false, true), true),
            Preset("Hitam Putih Harcourt", FilterParams(ColorLook.HARCOURT, 1f, LensFx.BLACK_MIST, 0.35f, true, true, 0.45f, false, true), true),
            Preset("Blockbuster", FilterParams(ColorLook.TEAL_ORANGE, 0.8f, LensFx.TIFFEN, 0.3f, true, true, 0.25f, true, true), true),
            Preset("Senja Mimpi", FilterParams(ColorLook.KODAK, 0.8f, LensFx.CINEBLOOM, 0.75f, true, true, 0.35f, true, true), true),
        )
    }
}
