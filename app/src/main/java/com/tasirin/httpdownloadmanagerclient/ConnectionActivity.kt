package com.tasirin.httpdownloadmanagerclient

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tasirin.httpdownloadmanagerclient.databinding.ActivityConnectionBinding
import kotlin.concurrent.thread

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private val prefs by lazy {
        getSharedPreferences("dm_client", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputHost.setText(prefs.getString(KEY_HOST, ""))
        binding.inputPort.setText(prefs.getInt(KEY_PORT, 8080).toString())
        binding.inputPin.setText(prefs.getString(KEY_PIN, ""))

        binding.btnConnect.setOnClickListener {
            doConnect()
        }
        binding.btnClear.setOnClickListener {
            binding.inputHost.text?.clear()
            binding.inputPort.setText("8080")
            binding.inputPin.text?.clear()
            binding.txtStatus.text = ""
            prefs.edit().clear().apply()
            Toast.makeText(this, "Disimpan", Toast.LENGTH_SHORT).show()
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
                    binding.txtStatus.setTextColor(Color.parseColor("#178A4C"))
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(EXTRA_BASE, result.baseUrl)
                            .putExtra(EXTRA_COOKIE, result.cookie.orEmpty())
                    )
                    finish()
                } else {
                    binding.txtStatus.text = getString(R.string.status_fail, result.message)
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
    }
}
