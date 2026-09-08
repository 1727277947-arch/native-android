package com.newsradar.app.ui

import com.newsradar.app.R
import com.newsradar.app.data.NewsClient

class IterationFragment : InfoFragment("策略迭代") {
    override fun reload() {
        Thread {
            try {
                val fbRes = NewsClient.feedback(requireContext())
                var like = 0; var dislike = 0
                val fb = fbRes.data as org.json.JSONArray
                for (i in 0 until fb.length()) {
                    if (fb.optJSONObject(i)?.optString("type") == "like") like++ else dislike++
                }
                val changeRes = NewsClient.changelog(requireContext())
                val change = changeRes.data as org.json.JSONArray
                onMain {
                    content.removeAllViews()
                    if (!fbRes.live) addText("（离线快照）", R.color.orange)
                    addTitle("反馈统计")
                    addText("点赞：" + like)
                    addText("踩：" + dislike)
                    if (like + dislike > 0) addText("当前采纳：点赞（+权重）、踩（-权重）")
                    addTitle("策略迭代记录")
                    if (change.length() > 0) {
                        for (i in change.length() - 1 downTo 0) {
                            val o = change.optJSONObject(i) ?: continue
                            addTitle(o.optString("version", "v?") + " · " + o.optString("date", ""))
                            addText(o.optString("summary", "-"))
                        }
                    } else addText("暂无迭代记录")
                }
            } catch (e: Exception) {
                onMain { showError() }
            }
        }.start()
    }
}
