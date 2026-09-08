package com.newsradar.app.ui

import com.newsradar.app.R
import com.newsradar.app.data.NewsClient

class RunLogFragment : InfoFragment("运行日志") {
    override fun reload() {
        Thread {
            try {
                val res = NewsClient.runlog(requireContext())
                val arr = res.data as org.json.JSONArray
                onMain {
                    content.removeAllViews()
                    if (!res.live) addText("（离线快照）", R.color.orange)
                    if (arr.length() == 0) { addText("暂无日志"); return@onMain }
                    for (i in arr.length() - 1 downTo 0) {
                        val o = arr.optJSONObject(i) ?: continue
                        val t = o.optString("time", o.optString("ts", ""))
                        val status = o.optString("status", "-")
                        val fetched = o.optInt("articles_fetched", -1)
                        var line = status
                        if (fetched >= 0) line += " · 抓取 " + fetched + " 篇"
                        if (!t.isBlank()) { addTitle(t); addText(line) }
                    }
                }
            } catch (e: Exception) {
                onMain { showError() }
            }
        }.start()
    }
}
