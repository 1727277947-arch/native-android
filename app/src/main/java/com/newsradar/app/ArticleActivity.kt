package com.newsradar.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean

class ArticleActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var translateBtn: TextView
    private lateinit var originalBtn: TextView
    private var url: String = ""
    private var viewingOriginal = false
    private val streaming = AtomicBoolean(false)
    private val sourceText = StringBuilder()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        url = intent.getStringExtra("url") ?: ""
        val title = intent.getStringExtra("title") ?: "文章阅读"
        titleView = findViewById(R.id.article_title)
        titleView.text = title
        progress = findViewById(R.id.article_progress)
        translateBtn = findViewById(R.id.article_translate)
        originalBtn = findViewById(R.id.article_original)

        findViewById<android.view.View>(R.id.article_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.article_open).setOnClickListener { openExternal() }

        originalBtn.setOnClickListener {
            if (viewingOriginal || streaming.get()) return@setOnClickListener
            viewingOriginal = true
            web.loadUrl(url)
        }
        translateBtn.setOnClickListener {
            if (streaming.get()) return@setOnClickListener
            startTranslate()
        }

        setupWebView()

        // 默认“快速阅读”：后台抓正文立即显示；失败才回退内嵌原网页
        showFastText("正在抓取正文，请稍候…")
        Thread {
            // Google News 等加密跳转链接先还原成真实 URL，否则打不开
            var finalUrl = url
            if (url.contains("news.google.com") && url.contains("/rss/articles")) {
                val r = ArticleText.resolveUrl(url)
                if (r != url && r.isNotBlank()) finalUrl = r
                runOnUiThread { this.url = finalUrl }
            }
            try {
                val html = ArticleText.fetch(finalUrl)
                val text = ArticleText.extract(finalUrl, html)
                sourceText.setLength(0); sourceText.append(text)
                runOnUiThread { showFastText(text) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "快速阅读不可用，已转到原网页", Toast.LENGTH_SHORT).show()
                    viewingOriginal = true
                    web.loadUrl(finalUrl)
                }
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web = findViewById(R.id.article_web)
        val st = web.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.setSupportZoom(true)
        st.builtInZoomControls = true
        st.displayZoomControls = false
        st.loadsImagesAutomatically = true
        st.useWideViewPort = true
        st.cacheMode = WebSettings.LOAD_DEFAULT
        if (Build.VERSION.SDK_INT >= 21) {
            try { st.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW } catch (e: Exception) {}
        }
        st.userAgentString = st.userAgentString.replace("; wv", "")

        web.setBackgroundColor(android.graphics.Color.parseColor("#0d1117"))
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (viewingOriginal) {
                    if (newProgress < 100) {
                        progress.visibility = android.view.View.VISIBLE
                        progress.progress = newProgress
                    } else {
                        progress.visibility = android.view.View.GONE
                    }
                }
            }
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= 21 && request != null) request.grant(request.resources)
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                return if (target.startsWith("http://") || target.startsWith("https://")) {
                    view?.loadUrl(target); true
                } else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, request!!.url)) } catch (e: Exception) {}
                    true
                }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (viewingOriginal) progress.visibility = android.view.View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (viewingOriginal) progress.visibility = android.view.View.GONE
            }
        }
    }

    private fun showFastText(text: String) {
        val body = text.ifBlank { "未能提取正文，可点“原文网页”查看。" }
        val out = "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" +
                "body{color:#e6edf3;background:#0d1117;font-size:16px;line-height:1.9;padding:16px;font-family:sans-serif}" +
                "h1{font-size:20px;color:#58a6ff}.p{margin:0 0 12px;text-align:justify}</style></head><body><h1>" +
                htmlEsc(titleView.text.toString()) + "</h1><div id=\"out\"><p class=\"p\">" +
                htmlEsc(body).replace("\n", "<br/>") + "</p></div></body></html>"
        viewingOriginal = false
        progress.visibility = android.view.View.GONE
        web.loadDataWithBaseURL(url, out, "text/html", "utf-8", null)
    }

    private fun startTranslate() {
        if (streaming.get()) return
        val t = sourceText.toString().trim()
        if (t.isNotEmpty()) { streamTranslate(t); return }
        // 来源为空（可能正停在原网页），从已加载网页取正文
        web.evaluateJavascript("(function(){return document.body.innerText;})()") { res ->
            val txt = try { (JSONTokener(res).nextValue() as? String)?.trim() ?: "" } catch (e: Exception) { "" }
            runOnUiThread { streamTranslate(txt) }
        }
    }

    private fun streamTranslate(source: String) {
        if (streaming.getAndSet(true)) return
        val t = source.trim()
        if (t.isEmpty()) {
            streaming.set(false)
            Toast.makeText(this, "未能获取正文", Toast.LENGTH_SHORT).show()
            return
        }
        viewingOriginal = false
        progress.visibility = android.view.View.GONE
        translateBtn.text = "翻译中…"
        translateBtn.isEnabled = false

        val base = "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" +
                "body{color:#e6edf3;background:#0d1117;font-size:16px;line-height:1.9;padding:16px;font-family:sans-serif}" +
                "h1{font-size:20px;color:#58a6ff}.p{margin:0 0 12px}.last{color:#3fb950;font-size:13px}</style></head><body>" +
                "<h1>" + htmlEsc(t.lines().firstOrNull()?.take(80) ?: "文章") + "</h1>" +
                "<div id=\"out\"></div><div class=\"last\" id=\"status\"></div></body></html>"
        web.loadDataWithBaseURL(url, base, "text/html", "utf-8", null)

        Thread {
            val sentences = ArticleText.splitSentences(t)
            var index = 0
            val total = sentences.size
            for (sent in sentences) {
                if (!streaming.get()) break
                try {
                    val zh = ArticleText.translateOne(sent)
                    val frag = htmlEsc(zh)
                    val jp = index + 1
                    runOnUiThread {
                        if (!streaming.get()) return@runOnUiThread
                        val js = "var o=document.getElementById('out');if(o){o.insertAdjacentHTML('beforeend'," +
                                JSONObject.quote("<p class=\"p\">" + frag + "</p>") + ");var s=document.getElementById('status');if(s){s.textContent='" + jp + "/" + total + "'}}" +
                                "window.scrollTo(0,document.body.scrollHeight);"
                        web.evaluateJavascript(js, null)
                    }
                    index++
                } catch (e: Exception) {}
            }
            runOnUiThread {
                streaming.set(false)
                translateBtn.isEnabled = true
                translateBtn.text = if (index >= total) "重新翻译" else "继续翻译"
            }
        }.start()
    }

    private fun htmlEsc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun openExternal() {
        if (url.isEmpty()) { Toast.makeText(this, "链接不可用", Toast.LENGTH_SHORT).show(); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
    }

    companion object {
        fun open(context: Context, title: String, url: String, summary: String = "") {
            val i = Intent(context, ArticleActivity::class.java)
            i.putExtra("title", title)
            i.putExtra("url", url)
            if (summary.isNotBlank()) i.putExtra("summary", summary)
            context.startActivity(i)
        }
    }

    override fun onBackPressed() {
        if (viewingOriginal && web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }
}
