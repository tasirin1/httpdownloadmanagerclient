package com.tasirin.httpdownloadmanagerclient

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object ServerApi {

    fun sha256(value: String): String = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    data class ConnectResult(
        val ok: Boolean,
        val message: String,
        val baseUrl: String = "",
        val cookie: String? = null
    )

    fun connect(host: String, port: Int, pin: String): ConnectResult {
        val base = "http://$host:$port"
        val cookie = if (pin.isNotEmpty()) "dm_pin=${sha256(pin.trim())}" else null
        return try {
            val conn = URL("$base/api/status").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            when (conn.responseCode) {
                401 -> ConnectResult(false, "PIN salah atau PIN diperlukan", base, cookie)
                200 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val battery = json.optInt("batteryPercent", -1)
                    val free = json.optLong("storageFree", 0)
                    val detail = buildString {
                        append(base)
                        if (battery >= 0) append(" · Baterai ${battery}%")
                        if (free > 0) append(" · Sisa ${formatBytes(free)}")
                    }
                    ConnectResult(true, detail, base, cookie)
                }
                else -> ConnectResult(false, "HTTP ${conn.responseCode}", base, cookie)
            }
        } catch (e: Exception) {
            ConnectResult(false, e.message ?: "tidak dapat terhubung", base, cookie)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024)
    }
}
