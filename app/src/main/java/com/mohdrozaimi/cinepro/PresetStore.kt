package com.mohdrozaimi.cinepro

import android.content.Context
import org.json.JSONArray

/** Simpan preset buatan sendiri dalam telefon (SharedPreferences). */
class PresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("cinepro_presets", Context.MODE_PRIVATE)

    fun userPresets(): List<Preset> {
        val raw = prefs.getString("list", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { Preset.fromJson(arr.getJSONObject(it)) }
    }

    fun save(preset: Preset) {
        val list = userPresets().filter { it.name != preset.name } + preset
        write(list)
    }

    fun delete(name: String) = write(userPresets().filter { it.name != name })

    private fun write(list: List<Preset>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun saveLast(params: FilterParams) {
        prefs.edit().putString("last", Preset("last", params).toJson().toString()).apply()
    }

    fun loadLast(): FilterParams? {
        val raw = prefs.getString("last", null) ?: return null
        return try { Preset.fromJson(org.json.JSONObject(raw))?.params } catch (e: Exception) { null }
    }
}
