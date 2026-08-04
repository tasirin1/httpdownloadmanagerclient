package com.tasirin.httpdownloadmanagerclient

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tasirin.httpdownloadmanagerclient.databinding.ActivityConnectionBinding
import kotlin.concurrent.thread

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private val prefs by lazy {
        getSharedPreferences("dm_client", Context.MODE_PRIVATE)
    }
    private var pendingUrl: String? = null
    private val successColor = Color.parseColor("#178A4C")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputHost.setText(prefs.getString(KEY_HOST, ""))
        binding.inputPort.setText(prefs.getInt(KEY_PORT, ServerApi.DEFAULT_PORT).toString())
        binding.inputPin.setText(prefs.getString(KEY_PIN, ""))

        binding.btnConnect.setOnClickListener {
            doConnect()
        }
        binding.btnDiscover.setOnClickListener {
            discoverServers()
        }
        binding.btnSendLink.setOnClickListener {
            sendLink()
        }
        binding.btnClear.setOnClickListener { clearSaved() }

        if (tryAutoForward(intent)) return
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (tryAutoForward(intent)) return
        handleIncomingIntent(intent)
    }

    private fun tryAutoForward(intent: Intent?): Boolean {
        val force = intent?.getBooleanExtra(EXTRA_FORCE, false) == true
        val savedBase = prefs.getString(KEY_BASE, null)
        if (force || savedBase.isNullOrEmpty()) return false
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_BASE, savedBase)
                .putExtra(EXTRA_COOKIE, prefs.getString(KEY_COOKIE, "").orEmpty())
                .putExtra(EXTRA_PENDING_URL, extractIncomingUrl(intent))
        )
        finish()
        return true
    }

    private fun clearSaved() {
        binding.inputHost.text?.clear()
        binding.inputPort.setText(ServerApi.DEFAULT_PORT.toString())
        binding.inputPin.text?.clear()
        binding.txtStatus.text = ""
        prefs.edit().clear().apply()
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    private fun extractIncomingUrl(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }
        return ServerApi.extractUrl(raw)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val url = extractIncomingUrl(intent)
        if (url != null) {
            pendingUrl = url
            binding.inputLink.setText(url)
            binding.linkCard.visibility = View.VISIBLE
        }
    }

    private fun doConnect() {
        val host = binding.inputHost.text?.toString()?.trim().orEmpty()
        val port = binding.inputPort.text?.toString()?.trim()?.toIntOrNull()
        val pin = binding.inputPin.text?.toString().orEmpty()
        if (host.isEmpty() || port == null || port !in 1..65535) {
            binding.txtStatus.text = getString(R.string.status_invalid)
            binding.txtStatus.setTextColor(Color.RED)
            return
        }
        binding.btnConnect.isEnabled = false
        binding.txtStatus.text = getString(R.string.status_connecting)
        binding.txtStatus.setTextColor(Color.GRAY)
        thread {
            val result = ServerApi.connect(host, port, pin)
            runOnUiThread {
                binding.btnConnect.isEnabled = true
                if (result.ok) {
                    prefs.edit()
                        .putString(KEY_HOST, host)
                        .putInt(KEY_PORT, port)
                        .putString(KEY_PIN, pin)
                        .putString(KEY_BASE, result.baseUrl)
                        .putString(KEY_COOKIE, result.cookie.orEmpty())
                        .apply()
                    binding.txtStatus.text = getString(R.string.status_ok, result.message)
                    binding.txtStatus.setTextColor(successColor)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(EXTRA_BASE, result.baseUrl)
                            .putExtra(EXTRA_COOKIE, result.cookie.orEmpty())
                            .putExtra(EXTRA_PENDING_URL, pendingUrl)
                    )
                    finish()
                } else {
                    binding.txtStatus.text = getString(R.string.status_fail, result.message)
                    binding.txtStatus.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun discoverServers() {
        val port = binding.inputPort.text?.toString()?.trim()?.toIntOrNull()
            ?: ServerApi.DEFAULT_PORT
        binding.btnDiscover.isEnabled = false
        binding.txtStatus.text = getString(R.string.status_searching)
        binding.txtStatus.setTextColor(Color.GRAY)
        thread {
            val servers = ServerApi.discoverServers(port)
            runOnUiThread {
                binding.btnDiscover.isEnabled = true
                when {
                    servers.isEmpty() -> {
                        binding.txtStatus.text = getString(R.string.status_none_found)
                        binding.txtStatus.setTextColor(Color.RED)
                    }
                    servers.size == 1 -> {
                        val s = servers[0]
                        binding.inputHost.setText(s.host)
                        binding.inputPort.setText(s.port.toString())
                        binding.txtStatus.text = getString(R.string.status_ok, s.url)
                        binding.txtStatus.setTextColor(successColor)
                        doConnect()
                    }
                    else -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_found_title)
                            .setItems(
                                servers.map { it.url }.toTypedArray()
                            ) { _, which ->
                                val s = servers[which]
                                binding.inputHost.setText(s.host)
                                binding.inputPort.setText(s.port.toString())
                                binding.txtStatus.text = getString(R.string.status_ok, s.url)
                                binding.txtStatus.setTextColor(successColor)
                                doConnect()
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                }
            }
        }
    }

    private fun sendLink() {
        val url = ServerApi.extractUrl(binding.inputLink.text?.toString())
        if (url == null) {
            binding.txtStatus.text = getString(R.string.link_need_connect)
            binding.txtStatus.setTextColor(Color.RED)
            return
        }
        val base = prefs.getString(KEY_BASE, null)
        if (base.isNullOrEmpty()) {
            binding.txtStatus.text = getString(R.string.link_need_connect)
            binding.txtStatus.setTextColor(Color.RED)
            return
        }
        val cookie = prefs.getString(KEY_COOKIE, null)
        binding.btnSendLink.isEnabled = false
        binding.txtStatus.text = getString(R.string.status_connecting)
        binding.txtStatus.setTextColor(Color.GRAY)
        thread {
            val error = ServerApi.addDownload(base, cookie, url)
            runOnUiThread {
                binding.btnSendLink.isEnabled = true
                if (error == null) {
                    binding.txtStatus.text = getString(R.string.link_sent)
                    binding.txtStatus.setTextColor(successColor)
                    binding.linkCard.visibility = View.GONE
                    pendingUrl = null
                } else {
                    binding.txtStatus.text = getString(R.string.link_send_failed, error)
                    binding.txtStatus.setTextColor(Color.RED)
                }
            }
        }
    }

    companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PIN = "pin"
        const val KEY_BASE = "base"
        const val KEY_COOKIE = "cookie"
        const val EXTRA_BASE = "extra_base"
        const val EXTRA_COOKIE = "extra_cookie"
        const val EXTRA_PENDING_URL = "extra_pending_url"
        const val EXTRA_FORCE = "extra_force"
    }
}
