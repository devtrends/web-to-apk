package net.devtrends.webtoapk

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#242424")

        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.setBackgroundColor(Color.parseColor("#242424"))

        // Configure CookieManager
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
        
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
          val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
          }
          startActivity(intent)
        }

        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://example.com")
    }
}
