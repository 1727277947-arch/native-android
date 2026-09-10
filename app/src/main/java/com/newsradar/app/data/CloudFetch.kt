package com.newsradar.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云端抓取触发器：App 手动刷新时，真正触发 GitHub Actions 定时抓取任务跑一遍，
 * 并轮询等待云端跑完，完成后上层再去拉最新数据。返回结果用于如实提示用户。
 */
object CloudFetch {

    // 仓库与工作流（数据抓取由这个仓库的 fetch.yml workflow 负责）
    private const val OWNER = "1727277947-arch"
    private const val REPO = "newsradar-fetch"
    private const val WF = "fetch.yml"

    // GitHub 令牌不写死在源码里：构建时由 keystore.properties 的 ghToken 注入 BuildConfig（该文件不入库）。

    private const val API = "https://api.github.com"
    private const val TMO = 8000
    private const val POLL_MS = 8000L
    private const val MAX_WAIT_MS = 260000L   // 抓取约1~3分钟，最多等约4.3分钟

    /** 触发 + 等待云端抓取完成。返回 true=云端已跑完（成败另见 ok）；started=false 表示通知没发出去（网络/鉴权失败）。 */
    class FetchResult(val started: Boolean, val ok: Boolean, val message: String)

    /** 触发云端抓取，并在后台线程里轮询，直到云端跑完或超时。调用方应放在后台线程。 */
    fun triggerAndWait(): FetchResult {
        if (com.newsradar.app.BuildConfig.GH_TOKEN.isBlank()) {
            return FetchResult(false, false, "未配置 GitHub 令牌：请在 keystore.properties 填写 ghToken 后重新构建")
        }
        // 1) 记录当前最新一次 dispatch 的 created_at，作为判断"我这次触发的 run"的基准
        val baseline = latestDispatchCreated()
        // 2) 发通知触发抓取
        if (!dispatch()) {
            return FetchResult(false, false, "通知云端失败：无法连接 GitHub（检查网络）")
        }
        // 3) 轮询直到云端跑完
        val deadline = System.currentTimeMillis() + MAX_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val (done, ok) = pollRun(baseline)
            if (done) {
                return FetchResult(true, ok, if (ok) "云端抓取已完成" else "云端抓取已结束（可能有源失败）")
            }
            Thread.sleep(POLL_MS)
        }
        return FetchResult(true, false, "云端抓取仍在进行（等待超时），已先加载当前最新数据")
    }

    private fun shortNow(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    /** 取最近一次 workflow_dispatch 运行的 created_at（UTC）。默认空字符串。 */
    private fun latestDispatchCreated(): String {
        return try {
            val url = "$API/repos/$OWNER/$REPO/actions/workflows/$WF/runs?per_page=5"
            val txt = get(url)
            if (txt.isBlank()) return ""
            val arr = JSONObject(txt).optJSONArray("workflow_runs") ?: return ""
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optString("event") == "workflow_dispatch") {
                    return r.optString("created_at", "")
                }
            }
            ""
        } catch (e: Exception) { "" }
    }

    /** 轮询：找出 baseline 之后新出现的、状态为 completed 的 workflow_dispatch run。返回 (是否结束, 是否成功)。 */
    private fun pollRun(baseline: String): Pair<Boolean, Boolean> {
        return try {
            val url = "$API/repos/$OWNER/$REPO/actions/workflows/$WF/runs?per_page=5"
            val txt = get(url)
            if (txt.isBlank()) return Pair(false, false)
            val arr = JSONObject(txt).optJSONArray("workflow_runs") ?: return Pair(false, false)
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optString("event") != "workflow_dispatch") continue
                val created = r.optString("created_at", "")
                if (created.isBlank()) continue
                if (created <= baseline) continue   // 还不是我触发的那个
                val status = r.optString("status", "")
                if (status == "completed") {
                    val concl = r.optString("conclusion", "")
                    return Pair(true, concl == "success")
                }
            }
            Pair(false, false)
        } catch (e: Exception) { Pair(false, false) }
    }

    private fun dispatch(): Boolean {
        // 国内访问 GitHub 不稳定，多试几次
        for (attempt in 1..3) {
            if (dispatchOnce()) return true
            try { Thread.sleep(2000L * attempt) } catch (_: Exception) {}
        }
        return false
    }

    private fun dispatchOnce(): Boolean {
        return try {
            val url = "$API/repos/$OWNER/$REPO/actions/workflows/$WF/dispatches"
            val body = JSONObject().put("ref", "main")
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = TMO
                conn.readTimeout = TMO
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "token " + com.newsradar.app.BuildConfig.GH_TOKEN)
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "NewsRadar-Android/1.2")
                val os: OutputStream = conn.outputStream
                os.write(body.toString().toByteArray(Charsets.UTF_8))
                os.close()
                val code = conn.responseCode
                code in 200..299
            } finally { conn.disconnect() }
        } catch (e: Exception) { false }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TMO
            conn.readTimeout = TMO
            conn.setRequestProperty("Authorization", "token " + com.newsradar.app.BuildConfig.GH_TOKEN)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NewsRadar-Android/1.2")
            if (conn.responseCode != 200) return ""
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            reader.forEachLine { sb.append(it).append('\n') }
            reader.close()
            return sb.toString()
        } finally { conn.disconnect() }
    }
}
