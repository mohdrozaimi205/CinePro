package com.mohdrozaimi.cinepro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Semak GitHub Releases (mohdrozaimi205/CinePro) untuk versi baru.
 * Tag release berbentuk v1.0.N — N dibandingkan dengan nombor binaan app ini.
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/mohdrozaimi205/CinePro/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/mohdrozaimi205/CinePro/releases/latest"

    data class Release(val tag: String, val build: Int, val apkUrl: String?, val notes: String)

    fun check(activity: Activity, manual: Boolean) {
        if (manual) Toast.makeText(activity, "Menyemak versi baru…", Toast.LENGTH_SHORT).show()
        Thread {
            val release = try { fetch() } catch (e: Exception) { null }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    release == null -> if (manual)
                        Toast.makeText(activity, "Tak dapat semak. Pastikan internet ON.", Toast.LENGTH_LONG).show()
                    release.build > BuildConfig.BUILD_NUMBER -> showDialog(activity, release)
                    manual -> Toast.makeText(activity, "Awak dah guna versi terbaru (${BuildConfig.VERSION_NAME}).", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun fetch(): Release? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val build = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apk = a.optString("browser_download_url"); break
                    }
                }
            }
            return Release(tag, build, apk, json.optString("body", "").trim())
        } finally {
            conn.disconnect()
        }
    }

    private fun showDialog(activity: Activity, r: Release) {
        AlertDialog.Builder(activity)
            .setTitle("Versi baru tersedia 🎬")
            .setMessage(
                "CinePro ${r.tag} dah ada.\nVersi awak sekarang: v${BuildConfig.VERSION_NAME}\n\n" +
                    "Tekan Muat Turun, kemudian buka fail APK untuk kemas kini. " +
                    "Preset dan tetapan awak tak akan hilang."
            )
            .setPositiveButton("Muat Turun") { _, _ ->
                val url = r.apkUrl ?: RELEASES_PAGE
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(activity, "Tiada pelayar untuk buka pautan.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Nanti", null)
            .show()
    }
}
