package com.newsradar.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.newsradar.app.R
import com.newsradar.app.data.NewsClient
import com.newsradar.app.data.NewsItem
import com.newsradar.app.data.RefreshLog
import com.newsradar.app.data.CloudFetch
import com.newsradar.app.ArticleText
import org.json.JSONArray

/** 国外新闻：中文简述（无原始链接，不跳转） */
class ForeignFragment : Fragment() {
    private lateinit var container: LinearLayout
    private lateinit var refresher: SwipeRefreshLayout
    @Volatile private var loading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        refresher = SwipeRefreshLayout(requireContext())
        refresher.setBackgroundResource(R.color.bg)
        refresher.setColorSchemeColors(0xFF58A6FF.toInt(), 0xFF3fb950.toInt(), 0xFFf85149.toInt())
        refresher.setOnRefreshListener { manualRefresh() }
        val root = NestedScrollView(requireContext())
        root.setBackgroundResource(R.color.bg)
        val col = LinearLayout(requireContext())
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, LinearLayout.LayoutParams(-1, -1))
        refresher.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this.container = col
        return refresher
    }

    override fun onResume() { super.onResume(); load() }


    /** 手动刷新：真正触发云端抓取一遍，跑完再拉最新数据。 */
    private fun manualRefresh() {
        if (loading) return
        loading = true
        if (::refresher.isInitialized && !refresher.isRefreshing) refresher.isRefreshing = true
        addTitle("国外行情 · 中文简述")
        val tv = TextView(requireContext())
        tv.text = "正在云端抓取最新数据，约需1~3分钟…\n完成后会自动刷新"
        tv.setTextColor(0xFF6f6273.toInt()); tv.gravity = Gravity.CENTER; tv.setPadding(0, 80, 0, 0)
        container.addView(tv)
        Thread {
            val res = CloudFetch.triggerAndWait()
            onMain {
                loading = false
                Toast.makeText(requireContext(), res.message, Toast.LENGTH_SHORT).show()
                load()
            }
        }.start()
    }

    private fun load() {
        if (loading) return
        loading = true
        container.removeAllViews()
        addTitle("国外行情 · 中文简述")
        if (::refresher.isInitialized && !refresher.isRefreshing) refresher.isRefreshing = true
        val tv = TextView(requireContext())
        tv.text = "加载中…"
        tv.setTextColor(0xFF6f6273.toInt()); tv.gravity = Gravity.CENTER; tv.setPadding(0, 80, 0, 0)
        container.addView(tv)
        Thread {
            try {
                val res = NewsClient.news(requireContext())
                val arr = res.data as JSONArray
                val items = mutableListOf<NewsItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tags = mutableListOf<String>()
                    val ta = o.optJSONArray("tags")
                    if (ta != null) for (j in 0 until ta.length()) tags.add(ta.optString(j))
                    if (!tags.contains("\u56fd\u5916")) continue   // 只显示国外
                    val kws = mutableListOf<String>()
                    val ka = o.optJSONArray("keywords")
                    if (ka != null) for (j in 0 until ka.length()) kws.add(ka.optString(j))
                    items.add(NewsItem(
                        o.optString("title"), o.optString("url"), o.optString("summary"),
                        o.optString("pub_time"), o.optString("source"), o.optInt("score", 0),
                        o.optString("priority"), o.optString("topic"), tags,
                        o.optBoolean("is_elite"), o.optString("dedup_key"), null,
                        o.optString("en_title", o.optString("title")), kws))
                }
                val live = res.live
                val foreignTotal = items.size
                onMain { render(items); finishRefresh(true, live, foreignTotal) }
            } catch (e: Exception) {
                onMain { renderError(); finishRefresh(false, false, 0) }
            }
        }.start()
    }

    private fun onMain(run: () -> Unit) {
        if (isAdded && activity != null) activity!!.runOnUiThread(run)
    }

    private fun finishRefresh(ok: Boolean, live: Boolean, count: Int = 0) {
        loading = false
        if (::refresher.isInitialized) refresher.isRefreshing = false
        val ctx = context
        if (ctx != null) {
            RefreshLog.record(ctx, "国外", ok, live, count, 0, "")
        }
        val msg = when {
            !ok -> "刷新失败，已保留上次数据"
            live -> "已获取云端最新国外 $count 条"
            else -> "已加载离线快照 $count 条（联网后自动更新最新）"
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun renderError() {
        container.removeAllViews()
        addTitle("国外行情 · 中文简述")
        val tv = TextView(requireContext())
        tv.text = "数据加载失败\n请检查网络后重试"
        tv.setTextColor(0xFF6f6273.toInt()); tv.gravity = Gravity.CENTER; tv.setPadding(0, 80, 0, 0)
        container.addView(tv)
    }

    private fun addTitle(text: String) {
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(14), dp(12), dp(2))
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(0xFF35293d.toInt()); tv.textSize = 18f; tv.setTypeface(null, Typeface.BOLD)
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)
        val btn = TextView(requireContext())
        btn.text = "↻ 刷新"
        btn.setTextColor(0xFF58A6FF.toInt()); btn.textSize = 13f
        btn.setPadding(dp(10), dp(6), dp(10), dp(6))
        btn.setBackgroundResource(R.drawable.bg_tag_selected)
        btn.setOnClickListener { manualRefresh() }
        row.addView(btn)
        container.addView(row)
    }

    private fun render(items: List<NewsItem>) {
        container.removeAllViews()
        addTitle("国外行情 · 中文简述")
        if (items.isEmpty()) {
            val e = TextView(requireContext())
            e.text = "暂无国外行情"
            e.setTextColor(0xFF6f6273.toInt()); e.gravity = Gravity.CENTER; e.setPadding(0, 80, 0, 0)
            container.addView(e)
            return
        }
        for (it in items) container.addView(card(it))
        val tip = TextView(requireContext())
        tip.text = "以上为抓取后自动翻译的中文简述，不含原文链接。"
        tip.setTextColor(0xFF5f677a.toInt()); tip.textSize = 11f; tip.setPadding(dp(16), dp(8), dp(16), dp(24))
        container.addView(tip)
    }

    private fun card(it: NewsItem): View {
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(8), dp(12), 0)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))

        val top = LinearLayout(requireContext())
        top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
        val cat = TextView(requireContext())
        cat.text = it.topic
        cat.setTextColor(0xFF58a6ff.toInt()); cat.textSize = 12f; cat.setTypeface(null, Typeface.BOLD)
        val catlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cat.layoutParams = catlp
        top.addView(cat)
        val src = TextView(requireContext())
        src.text = it.source
        src.setTextColor(0xFF6f6273.toInt()); src.textSize = 11f
        top.addView(src)
        card.addView(top)

        val title = TextView(requireContext())
        title.text = it.title
        title.setTextColor(0xFF35293d.toInt()); title.textSize = 15f; title.setTypeface(null, Typeface.BOLD)
        title.setLineSpacing(0f, 1.12f)
        title.setPadding(0, dp(8), 0, 0)
        card.addView(title)

        var sum: TextView? = null
        if (it.summary.isNotBlank()) {
            val sv = TextView(requireContext())
            sv.text = it.summary
            sv.setTextColor(0xFF5a4f66.toInt()); sv.textSize = 13f
            sv.setLineSpacing(0f, 1.15f)
            sv.setPadding(0, dp(6), 0, 0)
            sum = sv
            card.addView(sv)
        }

        if (it.keywords.isNotEmpty()) {
            val kw = TextView(requireContext())
            kw.text = "关键字：" + it.keywordsText
            kw.setTextColor(0xFFd29922.toInt()); kw.textSize = 12f
            kw.setPadding(0, dp(8), 0, 0)
            card.addView(kw)
        }

        // 端上兜底翻译：云端若因反爬/超时没译成功（仍是英文/空白），在手机本地译成中文简述
        val needTr = it.title.isNotBlank() &&
                (it.summary.isBlank() || isEnglish(it.title) || isEnglish(it.summary))
        if (needTr) {
            val zhHint = TextView(requireContext())
            zhHint.text = "中文简述翻译中…"
            zhHint.setTextColor(0xFF58a6ff.toInt()); zhHint.textSize = 12f
            zhHint.setPadding(0, dp(6), 0, 0)
            card.addView(zhHint)
            val cardRef = card
            val titleRef = title
            Thread {
                var gotEn = false
                try {
                    // 中文标题 + 中文简述（原文仅作兜底，展示以中文为准）
                    val srcEn = if (it.summary.isNotBlank()) it.summary else ""
                    var zhTitle = it.title
                    var zhSum = ""
                    if (isEnglish(it.enTitle)) {
                        val t = ArticleText.translateOne(it.enTitle).trim()
                        if (t.isNotBlank() && !isEnglish(t)) zhTitle = t
                    }
                    if (srcEn.isNotBlank() && isEnglish(srcEn)) {
                        val s = ArticleText.translateOne(srcEn).trim()
                        if (s.isNotBlank() && !isEnglish(s)) zhSum = s
                    }
                    val finalTitle = zhTitle
                    val finalSum = zhSum
                    gotEn = finalSum.isBlank() && isEnglish(srcEn)
                    if (isAdded && activity != null) {
                        activity!!.runOnUiThread {
                            try {
                                zhHint.visibility = View.GONE
                                titleRef.text = finalTitle
                                if (finalSum.isNotBlank()) {
                                    if (sum != null) sum!!.text = finalSum
                                    else addSummaryText(cardRef, finalSum)
                                }
                            } catch (ignore: Exception) {}
                        }
                    }
                } catch (ignore: Exception) {
                    gotEn = true
                } finally {
                    if (isAdded && activity != null) {
                        activity!!.runOnUiThread {
                            zhHint.visibility = View.GONE
                            if (gotEn && sum == null) addSummaryText(cardRef, "（原文暂未翻译）")
                        }
                    }
                }
            }.start()
        }
        return card
    }

    private fun addSummaryText(card: View, text: String) {
        val s = TextView(requireContext())
        s.text = text
        s.setTextColor(0xFF5a4f66.toInt()); s.textSize = 13f
        s.setLineSpacing(0f, 1.15f)
        s.setPadding(0, dp(6), 0, 0)
        if (card is LinearLayout) card.addView(s)
    }

    private fun isEnglish(s: String): Boolean {
        if (s.isBlank()) return false
        val latin = s.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cn = s.count { it.code in 0x4e00..0x9fa5 }
        return latin > cn && latin > 12
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
