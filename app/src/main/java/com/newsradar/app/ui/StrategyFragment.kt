package com.newsradar.app.ui

import com.newsradar.app.R
import com.newsradar.app.data.NewsClient
import org.json.JSONObject

class StrategyFragment : InfoFragment("精选策略") {
    override fun reload() {
        Thread {
            try {
                val res = NewsClient.strategy(requireContext())
                val o = res.data as JSONObject
                val weights = o.optJSONObject("weights")
                onMain {
                    content.removeAllViews()
                    if (!res.live) addText("（离线快照）", R.color.orange)
                    addTitle("策略版本 v" + o.optString("version", "?"))
                    addText("更新：" + o.optString("updated_at", "-") + " · 反馈 " + o.optInt("feedback_count", 0) + " 条")
                    if (weights != null) {
                        val names = mapOf(
                            "market_impact" to "市场影响力", "timeliness" to "时效性",
                            "credibility" to "可信度", "actionability" to "可操作性",
                            "noise_penalty" to "噪音惩罚", "tech_depth" to "技术深度",
                            "novelty" to "新颖度", "utility" to "实用性", "ad_penalty" to "广告检测"
                        )
                        for (k in weights.keys().asSequence().sorted()) {
                            val pct = Math.round(weights.optDouble(k, 0.0) * 100.0).toInt()
                            addText((names[k] ?: k) + "：" + pct + "%")
                        }
                    } else addText("暂无策略数据")
                }
            } catch (e: Exception) {
                onMain { showError() }
            }
        }.start()
    }
}
