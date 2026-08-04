package com.tasirin.httpdownloadmanagerclient

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.InputType
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tasirin.httpdownloadmanagerclient.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var baseUrl: String = ""
    private var cookie: String = ""
    private var errorDialogShown = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var savedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val fabHandler = Handler(Looper.getMainLooper())
    private val fabHideRunnable = Runnable { binding.fabMenu.hide() }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uris = when {
                data?.clipData != null && data.clipData!!.itemCount > 0 ->
                    Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            callback.onReceiveValue(uris)
        } else {
            callback.onReceiveValue(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        baseUrl = intent.getStringExtra(ConnectionActivity.EXTRA_BASE)
            ?: prefsString(ConnectionActivity.KEY_BASE)
            ?: return
        cookie = intent.getStringExtra(ConnectionActivity.EXTRA_COOKIE)
            ?: prefsString(ConnectionActivity.KEY_COOKIE)
            ?: ""

        setupCookie()
        setupWebView()
        binding.webView.loadUrl(baseUrl + "/")

        binding.fabMenu.setOnClickListener {
            showFabMenu()
        }

        intent.getStringExtra(ConnectionActivity.EXTRA_PENDING_URL)?.let {
            showSendLinkDialog(it)
        }
        showFabTemporarily()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ConnectionActivity.EXTRA_PENDING_URL)?.let {
            showSendLinkDialog(it)
        }
        showFabTemporarily()
    }

    private fun prefsString(key: String): String? =
        getSharedPreferences("dm_client", Context.MODE_PRIVATE).getString(key, null)

    private fun setupCookie() {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        if (cookie.isNotEmpty()) {
            manager.setCookie(baseUrl, "$cookie; Path=/")
        }
        runCatching { manager.flush() }
    }

    private fun setupWebView() {
        val web = binding.webView
        val settings = web.settings
        web.setOnTouchListener { _, _ ->
            showFabTemporarily()
            false
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.setSupportMultipleWindows(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (view?.canGoBack() == false) showConnectionError()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && view?.canGoBack() == false) {
                    showConnectionError()
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.visibility = if (newProgress >= 100) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                binding.progress.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                intent?.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                fileChooserLauncher.launch(
                    intent ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                )
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = web
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(
                view: View?,
                callback: CustomViewCallback?
            ) {
                if (view == null) return
                customView = view
                customViewCallback = callback
                savedOrientation = requestedOrientation
                runCatching {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                binding.fullscreenContainer.removeAllViews()
                binding.fullscreenContainer.addView(view)
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.progress.visibility = View.GONE
                binding.fabMenu.hide()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return@DownloadListener
            val request = DownloadManager.Request(Uri.parse(url))
            CookieManager.getInstance().getCookie(url)?.let {
                request.addRequestHeader("Cookie", it)
            }
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            val fileName = fileNameFrom(url, contentDisposition)
            runCatching {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            dm.enqueue(request)
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        })
    }

    private fun fileNameFrom(url: String, contentDisposition: String?): String {
        contentDisposition?.let { cd ->
            Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").find(cd)?.groupValues?.get(1)?.let {
                val decoded = runCatching {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }.getOrDefault(it)
                val clean = decoded.replace(Regex("[/\\\\]"), "_").trim()
                if (clean.isNotEmpty()) return clean
            }
        }
        val path = Uri.parse(url).lastPathSegment.orEmpty()
        val fallback = path.substringAfterLast('/').trim()
        return fallback.ifEmpty { "download_${System.currentTimeMillis()}" }
    }

    private fun showFabTemporarily() {
        binding.fabMenu.show()
        fabHandler.removeCallbacks(fabHideRunnable)
        fabHandler.postDelayed(fabHideRunnable, 3500)
    }

    private fun hideCustomView() {
        val callback = customViewCallback
        customView?.let { binding.fullscreenContainer.removeView(it) }
        customView = null
        customViewCallback = null
        callback?.onCustomViewHidden()
        binding.fullscreenContainer.visibility = View.GONE
        runCatching {
            requestedOrientation = savedOrientation
        }
        binding.fabMenu.show()
    }

    private fun showFabMenu() {
        fabHandler.removeCallbacks(fabHideRunnable)
        val options = arrayOf(
            getString(R.string.action_refresh),
            getString(R.string.menu_send_link),
            getString(R.string.action_browser),
            getString(R.string.menu_change_server)
        )
        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> binding.webView.reload()
                    1 -> showSendLinkDialog(null)
                    2 -> openInBrowser()
                    3 -> changeServer()
                }
            }
            .show()
    }

    private fun openInBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeServer() {
        startActivity(
            Intent(this, ConnectionActivity::class.java)
                .putExtra(ConnectionActivity.EXTRA_FORCE, true)
        )
        finish()
    }

    private fun showConnectionError() {
        if (errorDialogShown) return
        errorDialogShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_connect_title)
            .setMessage(R.string.error_connect_msg)
            .setPositiveButton(R.string.action_retry) { _, _ ->
                errorDialogShown = false
                binding.webView.reload()
            }
            .setNegativeButton(R.string.menu_change_server) { _, _ ->
                changeServer()
            }
            .setOnDismissListener { errorDialogShown = false }
            .show()
    }

    private fun showSendLinkDialog(prefill: String?) {
        val input = EditText(this)
        input.hint = getString(R.string.link_prompt_hint)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(prefill.orEmpty())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.link_prompt_title)
            .setView(input)
            .setPositiveButton(R.string.action_send) { _, _ ->
                val url = ServerApi.extractUrl(input.text?.toString())
                if (url == null) return@setPositiveButton
                thread {
                    val error = ServerApi.addDownload(baseUrl, cookie, url)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (error == null) getString(R.string.link_sent)
                            else getString(R.string.link_send_failed, error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onBackPressed() {
        if (customView != null) {
            hideCustomView()
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        fabHandler.removeCallbacks(fabHideRunnable)
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        binding.webView.destroy()
        super.onDestroy()
    }
}
