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
    private const val TMO_CONNECT = 6000
    private const val TMO_READ = 8000

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

    private fun fetchText(url: String): String {
        return try {
            httpGet(url)
        } catch (e: Exception) {
            // 主源失败则回退到备用源(jDelivr)
            if (url.startsWith(LIVE_BASE)) httpGet(url.replace(LIVE_BASE, LIVE_BASE_ALT))
            else throw e
        }
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
