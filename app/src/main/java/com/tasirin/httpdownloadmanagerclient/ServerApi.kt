package com.tasirin.httpdownloadmanagerclient

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

object ServerApi {

    private val URL_TOKEN_REGEX = Regex("\\s+")

    const val DEFAULT_PORT = 8080
    private const val MAX_HOSTS = 254
    private const val SCAN_THREADS = 24

    data class ServerInfo(val host: String, val port: Int) {
        val url: String get() = "http://$host:$port"
    }

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

    /** Memindai subnet lokal untuk mencari server Download Manager pada port tertentu. */
    fun discoverServers(port: Int): List<ServerInfo> {
        val ips = localIpv4s()
        val ports = listOf(port, DEFAULT_PORT).distinct()
        val found = Collections.synchronizedList(mutableListOf<ServerInfo>())
        if (ips.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(SCAN_THREADS)
        val futures = mutableListOf<Future<*>>()
        for (ip in ips) {
            val base = ip.substringBeforeLast('.')
            for (i in 1..MAX_HOSTS) {
                val host = "$base.$i"
                if (host == ip) continue
                for (p in ports) {
                    futures.add(
                        pool.submit {
                            runCatching {
                                val conn = URL("http://$host:$p/").openConnection() as HttpURLConnection
                                conn.connectTimeout = 400
                                conn.readTimeout = 800
                                conn.instanceFollowRedirects = false
                                if (conn.responseCode == 200) {
                                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                                    if (body.contains("Download Manager")) {
                                        found.add(ServerInfo(host, p))
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        futures.forEach { runCatching { it.get() } }
        pool.shutdown()
        return found.distinct()
    }

    private fun localIpv4s(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .map { it.hostAddress.orEmpty() }
            }
            .filter { it.isNotEmpty() && it.count { c -> c == '.' } == 3 }
    }.getOrDefault(emptyList())

    /** Mengirim link download ke server. Mengembalikan null jika sukses, atau pesan error. */
    fun addDownload(base: String, cookie: String?, url: String): String? {
        return try {
            val conn = URL("$base/api/add").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "url=" + URLEncoder.encode(url, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.optBoolean("ok", false)) null
                else json.optString("error").ifEmpty { "server menolak" }
            } else {
                "HTTP ${conn.responseCode}"
            }
        } catch (e: Exception) {
            e.message ?: "gagal terhubung"
        }
    }

    /** Mengambil URL http/https pertama dari teks (misal isi share atau clipboard). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return text.trim().split(URL_TOKEN_REGEX).firstOrNull {
            it.startsWith("http://") || it.startsWith("https://")
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
