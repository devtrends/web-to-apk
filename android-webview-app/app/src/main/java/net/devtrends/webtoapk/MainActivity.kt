
package net.devtrends.webtoapk

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val homeUrl = "https://example.com"
    private val homeUri = Uri.parse(homeUrl)
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg)

        webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("blob:")) {
                val fileName = if (contentDisposition != null && contentDisposition.contains("filename")) {
                    var result = contentDisposition.substringAfter("filename=", "")
                    result = result.trim('"', '\'', ';', ' ')
                    if (result.contains("UTF-8''")) {
                        result = result.substringAfter("UTF-8''")
                    }
                    result
                } else {
                    URLUtil.guessFileName(url, contentDisposition, mimetype)
                }

                webView.post {
                    val script = """
                        (async function() {
                          const response = await fetch(${JSONObject.quote(url)});
                          const blob = await response.blob();
                          const reader = new FileReader();
                          reader.onloadend = function() {
                            Android.saveBlob(reader.result, ${JSONObject.quote(fileName)}, ${JSONObject.quote(mimetype ?: "application/octet-stream")});
                          };
                          reader.readAsDataURL(blob);
                        })();
                        """.trimIndent()
                    webView.evaluateJavascript(
                        script,
                        null
                    )
                }
                Toast.makeText(applicationContext, "Downloading export...", Toast.LENGTH_SHORT).show()
                return@setDownloadListener
            }

            val request = DownloadManager.Request(Uri.parse(url))

            val cookies = cookieManager.getCookie(url)
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies)
            }

            request.addRequestHeader("User-Agent", userAgent)
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val fileName = if (contentDisposition != null && contentDisposition.contains("filename")) {
              var result = contentDisposition.substringAfter("filename=", "")
              result = result.trim('"', '\'', ';', ' ')
              if (result.contains("UTF-8''")) {
                result = result.substringAfter("UTF-8''")
              }
              result
            } else {
              URLUtil.guessFileName(url, contentDisposition, mimetype)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                request.setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(applicationContext, "Downloading export...", Toast.LENGTH_SHORT).show()
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun saveBlob(dataUrl: String, fileName: String, mimeType: String) {
                val commaIndex = dataUrl.indexOf(',')
                if (commaIndex == -1) return

                val data = Base64.decode(dataUrl.substring(commaIndex + 1), Base64.DEFAULT)
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                dir.mkdirs()

                val outFile = File(dir, fileName.ifBlank { "download" })
                FileOutputStream(outFile).use { it.write(data) }

                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Saved ${outFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            if (isExternalUrl(url)) {
              startActivity(Intent(Intent.ACTION_VIEW, url))
              return true
            }
            return false
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()
          }

          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
              if (request.isForMainFrame) {
                view?.loadUrl("file:///android_asset/no_internet.html")
              }
          }
        }

        webView.loadUrl(homeUrl)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
      super.onPause()
      CookieManager.getInstance().flush()
    }

    override fun onStop() {
      super.onStop()
      CookieManager.getInstance().flush()
    }

    private fun isExternalUrl(url: Uri): Boolean {
        if (!url.scheme.equals("http", ignoreCase = true) &&
            !url.scheme.equals("https", ignoreCase = true)) {
            return true
        }

        return url.host != homeUri.host || url.scheme != homeUri.scheme
    }
}
