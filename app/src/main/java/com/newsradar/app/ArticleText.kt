package com.newsradar.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 阅读页辅助：快速抓取正文、联网翻译成中文。
 * 不需要额外依赖：用正则剥离脚本/样式/标签，再通过 Google 免费翻译接口翻译。
 */
object ArticleText {

    private const val TMO = 10000
    private const val UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"

    /** 跟随跳转，返回最终真实 URL（用于 Google News 等重定向链接） */
    fun resolveUrl(url: String): String {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TMO
                conn.readTimeout = TMO
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
                conn.connect()
                val final = conn.url.toString()
                if (final.isNotBlank() && "news.google.com/rss" !in final) final else url
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) { url }
    }

    /** 抓取网页源码（带超时，快速失败） */
    fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TMO
            conn.readTimeout = TMO
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP " + conn.responseCode)
            val ctype = conn.contentType ?: ""
            val charset = parseCharset(ctype)
            val reader = BufferedReader(InputStreamReader(conn.inputStream, charset))
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var n: Int
            while (reader.read(buf).also { n = it } > 0) sb.append(buf, 0, n)
            reader.close()
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCharset(ct: String?): String {
        if (ct != null) {
            val m = Regex("charset=([a-zA-Z0-9-]+)").find(ct)
            if (m != null) return m.groupValues[1]
        }
        return "UTF-8"
    }

    /**
     * 从 HTML 提取可见正文文本。优先级：<title> + <h1>/<h2> + <p>。失败时退回纯文本剥离。
     */
    fun extract(url: String, html: String): String {
        val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()

        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append(title).append("\n\n")
        // 优先取正文段落 <p>
        val para = Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)
        val found = para.findAll(html).toList()
        for (m in found) {
            val t = cleanNode(m.groupValues[1])
            if (t.length >= 20) sb.append(t).append("\n\n")
        }
        // 若段落太少，退一步取 <h1>/<h2>/<div> 文本
        if (sb.length < 300) {
            sb.clear()
            if (title.isNotBlank()) sb.append(title).append("\n\n")
            val any = Regex("<(?:h1|h2|div|article|section)[^>]*>(.*?)</(?:h1|h2|div|article|section)>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            for (m in any.findAll(html)) {
                val t = cleanNode(m.groupValues[2])
                if (t.length >= 30) sb.append(t).append("\n\n")
                if (sb.length > 20000) break
            }
        }
        var out = sb.toString().trim()
        if (out.length < 80) {
            out = plainStrip(html).trim()
        }
        return out.ifBlank { "未能提取正文，请点击\u201c原文网页\u201d查看。" }
    }

    private fun cleanNode(raw: String): String {
        var s = raw
        s = s.replace(Regex("<script.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<style.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[ \t\r\n]+"), " ").trim()
        return s
    }

    private fun plainStrip(html: String): String {
        var s = html
        s = s.replace(Regex("<script.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<style.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[ \t\r\n]+"), " ").trim()
        return s
    }

    fun decodeEntities(s: String): String {
        var out = s
        val map = mapOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
            "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'"
        )
        for ((k, v) in map) out = out.replace(k, v)
        out = out.replace(Regex("&#(\\d+);")) { m -> (m.groupValues[1].toIntOrNull() ?: 0).toChar().toString() }
        out = out.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
        }
        return out
    }

    /**
     * 调用 Google 免费翻译接口，把整段文本翻译成简体中文。
     * 返回翻译后的纯文本；失败抛异常由调用方兜底。
     */
    fun translate(text: String): String {
        val sb = StringBuilder()
        // 分段翻译，太长一次提交容易失败/超时
        val paragraphs = text.split("\n{2,}")
        for (para in paragraphs) {
            val t = para.trim()
            if (t.isEmpty()) { sb.append("\n\n"); continue }
            // 每段若过长再按句切分，逐批提交
            val chunks = chunk(t, 1200)
            for (c in chunks) {
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=" +
                        URLEncoder.encode(c, StandardCharsets.UTF_8.toString())
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = TMO
                    conn.readTimeout = TMO
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", UA)
                    if (conn.responseCode != 200) throw RuntimeException("HTTP " + conn.responseCode)
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8.toString()))
                    val resp = reader.use { it.readText() }
                    sb.append(parseTranslated(resp))
                } finally {
                    conn.disconnect()
                }
            }
            sb.append("\n\n")
        }
        val res = sb.toString().trim()
        if (res.isBlank()) throw RuntimeException("翻译结果为空")
        return res
    }

    private fun chunk(text: String, size: Int): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (sent in text.split(Regex("(?<=[.!?。！？])"))) {
            if (cur.length + sent.length > size && cur.isNotEmpty()) {
                out.add(cur.toString()); cur = StringBuilder()
            }
            cur.append(sent)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return if (out.isEmpty()) listOf(text) else out
    }

    /** 解析 Google 翻译返回的 JSON，拼接译文 */
    private fun parseTranslated(resp: String): String {
        // Google 翻译返回 [[["译文","原文",null,null,...],...],null,"en",...]
        return try {
            val top = org.json.JSONArray(resp)
            val segs = top.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segs.length()) {
                val seg = segs.getJSONArray(i)
                if (seg.length() > 0) sb.append(seg.getString(0))
            }
            val out = sb.toString().trim()
            if (out.isEmpty()) resp else out
        } catch (e: Exception) {
            resp
        }
    }

    /** 单句翻译（用于流式逐句输出）：优先百度翻译，失败回退 Google */
    fun translateOne(text: String): String {
        if (text.isBlank()) return text
        val c = text.take(900)
        try {
            val out = translateBaidu(c)
            if (out.isNotBlank()) return out
        } catch (e: Exception) { /* fall through */ }
        return translateGoogle(c)
    }

    /** 百度翻译（免费网页接口，无需 key） */
    private fun translateBaidu(text: String): String {
        val params = "query=" + URLEncoder.encode(text, StandardCharsets.UTF_8.toString()) + "&from=auto&to=zh"
        val url = "https://fanyi.baidu.com/transapi" + "?" + params
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TMO
            conn.readTimeout = TMO
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            if (conn.responseCode != 200) throw RuntimeException("HTTP " + conn.responseCode)
            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8.toString()))
            val resp = reader.use { it.readText() }
            val j = org.json.JSONObject(resp)
            val arr = j.optJSONArray("data")
            if (arr != null) {
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    val dst = o?.optString("dst")
                    if (!dst.isNullOrBlank()) sb.append(dst)
                }
                val out = sb.toString().trim()
                if (out.isNotEmpty()) return out
            }
            return ""
        } finally {
            conn.disconnect()
        }
    }

    /** Google 翻译兜底 */
    private fun translateGoogle(text: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=" +
                URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TMO
            conn.readTimeout = TMO
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode != 200) throw RuntimeException("HTTP " + conn.responseCode)
            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8.toString()))
            val resp = reader.use { it.readText() }
            return parseTranslated(resp).trim()
        } finally {
            conn.disconnect()
        }
    }

    /** 把正文按句子切分，每批不超过 maxLen，便于流式逐句翻译 */
    fun splitSentences(text: String, maxLen: Int = 600): List<String> {
        val out = mutableListOf<String>()
        val parts = text.split("\n{2,}")
        for (para in parts) {
            val t = para.trim().replace("\n", " ").trim()
            if (t.isEmpty()) continue
            if (t.length <= maxLen) { out.add(t); continue }
            var cur = StringBuilder()
            for (sent in t.split(Regex("(?<=[.!?。！？])"))) {
                if (sent.trim().isEmpty()) continue
                if (cur.isNotEmpty() && cur.length + sent.length > maxLen) { out.add(cur.toString()); cur = StringBuilder() }
                cur.append(sent)
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return out
    }

    /** 把纯文本包装成深色主题可读 HTML */
    fun wrapHtml(text: String): String {
        val esc = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br/>")
        val start = text.take(1200)
        val isEn = start.count { it in 'a'..'z' || it in 'A'..'Z' } > start.length / 3
        val hint = if (isEn) "中文翻译加载中…" else ""
        return "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>body{color:#e6edf3;background:#0d1117;font-size:16px;line-height:1.8;padding:16px;font-family:sans-serif}" +
                "h1{font-size:20px}.note{color:#8b949e;font-size:13px;margin-bottom:8px}</style></head><body>" +
                (if (hint.isEmpty()) "" else "<div class=\"note\">$hint</div>") + "<p>" + esc + "</p></body></html>"
    }
}
