package com.newsradar.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object NewsClient {
    private const val LIVE_BASE = "https://gitee.com/ys15251239086/shuobao-gitee/raw/master/data"
    private const val LIVE_BASE_ALT = "https://cdn.jsdelivr.net/gh/1727277947-arch/newsradar-fetch@main/data"
    // 超时：实测到 gitee/github 的往返有 400~500ms，长响应(TLS握手+首包+全量JSON)在弱信号下
    // 会明显超过原来 6s/8s 的预算，导致"看着有网却报连不上"。放宽到 12s/20s，配合上面的重试，
    // 换来的是失败率大幅下降（真断网时也仍是有限等待，不会一直转圈）。
    private const val TMO_CONNECT = 12000
    private const val TMO_READ = 20000
    val NEWS_URL = "$LIVE_BASE/news.json"
    val STRATEGY_URL = "$LIVE_BASE/strategy.json"
    val CHANGELOG_URL = "$LIVE_BASE/strategy_changelog.json"
    val FEEDBACK_URL = "$LIVE_BASE/feedback.json"
    val PRICES_URL = "$LIVE_BASE/prices.json"
    val RUNLOG_URL = "$LIVE_BASE/run_log.json"

    /** 返回结果：live 或 offlive，data 为 JSON */
    class Result(val live: Boolean, val data: Any)

    fun news(ctx: Context): Result {
        return try {
            Result(true, JSONArray(fetchText(NEWS_URL)))
        } catch (e: Exception) {
            Result(false, JSONArray(assetText(ctx, "news.json")))
        }
    }

    /** 大宗商品现货/期货报价：返回封装对象 */
    fun prices(ctx: Context): Result {
        return try {
            Result(true, JSONObject(fetchText(PRICES_URL)))
        } catch (e: Exception) {
            Result(false, JSONObject(assetText(ctx, "prices.json")))
        }
    }

    fun strategy(ctx: Context): Result {
        return try {
            Result(true, JSONObject(fetchText(STRATEGY_URL)))
        } catch (e: Exception) {
            Result(false, JSONObject(assetText(ctx, "strategy.json")))
        }
    }

    fun runlog(ctx: Context): Result {
        return try {
            Result(true, JSONArray(fetchText(RUNLOG_URL)))
        } catch (e: Exception) {
            Result(false, JSONArray(assetText(ctx, "run_log.json")))
        }
    }

    fun changelog(ctx: Context): Result {
        return try {
            Result(true, JSONArray(fetchText(CHANGELOG_URL)))
        } catch (e: Exception) {
            Result(false, JSONArray("[]"))
        }
    }

    fun feedback(ctx: Context): Result {
        return try {
            Result(true, JSONArray(fetchText(FEEDBACK_URL)))
        } catch (e: Exception) {
            Result(false, JSONArray("[]"))
        }
    }

    /**
     * 拉取远端文本。
     *
     * 原实现是"主源试一次、失败就换备源试一次"，只有两发子弹：国内网络抖动、丢一个包、
     * 或 TLS 握手慢一点，整页就直接判定失败并回退到 App 内置的旧数据 —— 这就是
     * "数据连不上 / 一直是旧数据"的主要来源。现在改为：
     *   1) 主源与备源各自重试 2 次（带退避），任一成功即返回；
     *   2) 只有主源连续失败才切备源，不再"一枪没中就换枪"；
     *   3) 全部失败时把最后一次的真实原因抛出去，让界面能如实提示，而不是静默装正常。
     */
    private fun fetchText(url: String): String {
        val alt = if (url.startsWith(LIVE_BASE)) url.replace(LIVE_BASE, LIVE_BASE_ALT) else null
        val sources = if (alt != null) listOf(url, alt) else listOf(url)
        var lastErr = ""
        for (src in sources) {
            for (attempt in 1..2) {
                try {
                    return httpGet(src)
                } catch (e: Exception) {
                    lastErr = e.message ?: e.javaClass.simpleName
                    if (attempt < 2) {
                        try { Thread.sleep(350L * attempt) } catch (_: Exception) {}
                    }
                }
            }
        }
        throw RuntimeException(lastErr.ifBlank { "网络不可用" })
    }
    private fun httpGet(url: String): String {
        // 每次刷新强制拉最新数据：加时间戳破坏缓存 + 关闭HTTP缓存 + no-cache头
        val sep = if (url.contains("?")) "&" else "?"
        val cacheBust = url + sep + "_ts=" + System.currentTimeMillis()
        val conn = URL(cacheBust).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TMO_CONNECT
            conn.readTimeout = TMO_READ
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "NewsRadar-Android/1.2")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Pragma", "no-cache")
            if (conn.responseCode != 200) throw RuntimeException("HTTP " + conn.responseCode)
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            reader.forEachLine { sb.append(it).append('\n') }
            reader.close()
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    private fun assetText(ctx: Context, name: String): String {
        val reader = BufferedReader(InputStreamReader(ctx.assets.open(name)))
        val sb = StringBuilder()
        reader.forEachLine { sb.append(it).append('\n') }
        reader.close()
        return sb.toString()
    }
}
