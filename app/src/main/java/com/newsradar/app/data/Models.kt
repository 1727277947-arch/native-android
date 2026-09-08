package com.newsradar.app.data

import org.json.JSONObject

class NewsItem(
    var title: String,
    val url: String,
    val summary: String,
    val pubTime: String,
    val source: String,
    val score: Int,
    val priority: String,
    val topic: String,
    val tags: List<String>,
    val isElite: Boolean,
    val dedupKey: String,
    val breakdown: JSONObject?,
    val enTitle: String,
    val keywords: List<String>
) {
    var liked: Boolean = false
    var disliked: Boolean = false
    var starred: Boolean = false
    val tagsText: String get() = tags.joinToString(" \u00b7 ")
    val keywordsText: String get() = keywords.joinToString(" · ")
    val isKeywordOnly: Boolean get() = url.isBlank()
}

class PriceItem(
    val symbol: String,
    val name: String,
    val unit: String,
    val changePct: Double,
    val trend: String,
    val category: String,
    val market: String,
    val note: String,
    val spot: Double?,
    val future: Double?,
    val basis: Double?,
    val basisPct: Double?,
    val boardUp: Double? = null,
    val boardDown: Double? = null,
    val guideLabel: String = "",
    val guideAction: String = "观望",
    val guideEntry: Double? = null,
    val guideTp: Double? = null,
    val guideSl: Double? = null,
    val guideSupport: Double? = null,
    val guideResist: Double? = null,
    val guideReason: String = "",
    val guideRr: Int = 0,
    val guideRisk: Double? = null,
    val guidePyramid: String = "",
    val guideMoveStop: String = "",
    val guideTrailStop: Double? = null,
    val guideExit: Double? = null,
    val guideStopDiscipline: String = "",
    val estMargin: Double = 0.0,
    val dayRange: Double = 0.0
) {
    // 卡片主价：优先期货，无期货则用现货
    val price: Double get() = future ?: spot ?: 0.0
    val hasBoth: Boolean get() = spot != null && future != null
    val priceText: String get() = if (price >= 100) "%.2f".format(price) else "%.4f".format(price)
    val hands100k: Int get() = if (estMargin > 0) kotlin.math.max(1, (100000.0 / estMargin).toInt()) else 0
    fun fmt(v: Double): String = if (v >= 100) "%.0f".format(v) else if (v >= 1) "%.2f".format(v) else "%.4f".format(v)
    val chgText: String get() =
        if (changePct > 0) "+%.2f%%".format(changePct)
        else if (changePct < 0) "%.2f%%".format(changePct)
        else "持平"
    val basisText: String get() {
        if (basis == null) return ""
        val s = if (basis!! >= 0) "+" else ""
        return "$s${fmt(basis!!)}"
    }
    val basisPctText: String get() =
        if (basisPct != null) (if (basisPct!! > 0) "+" else "") + "%.1f%%".format(basisPct!!) else ""
}

class RunLog(val time: String, val log: String, val ok: Boolean)

class FeedbackEntry(val title: String, val source: String, val type: String, val note: String, val time: String)
