package com.tasirin.httpdownloadmanagerclient

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tasirin.httpdownloadmanagerclient.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var baseUrl: String = ""
    private var cookie: String = ""

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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = baseUrl.removePrefix("http://")
        supportActionBar?.subtitle = getString(R.string.app_name)

        setupCookie()
        setupWebView()
        binding.webView.loadUrl(baseUrl + "/")
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
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.visibility = if (newProgress >= 100) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                binding.webView.reload()
                true
            }
            R.id.action_browser -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl))
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_disconnect -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        binding.webView.destroy()
        super.onDestroy()
    }
}
