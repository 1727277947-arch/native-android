package com.newsradar.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.EditText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.newsradar.app.data.NewsClient
import com.newsradar.app.data.NewsItem
import org.json.JSONArray
import com.newsradar.app.data.RefreshLog
import com.newsradar.app.data.CloudFetch
import com.newsradar.app.data.PriceItem
import android.graphics.Color
import android.graphics.Typeface
import org.json.JSONObject

class NewsFragment : Fragment() {
    private val all = mutableListOf<NewsItem>()
    private val shown = mutableListOf<NewsItem>()
    private lateinit var list: RecyclerView
    private lateinit var empty: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var pricePanel: LinearLayout
    private lateinit var priceRow: LinearLayout
    private lateinit var refresher: SwipeRefreshLayout
    @Volatile private var loading = false
    private var query = ""
    private var tag = "全部"
    private var adapter: NewsAdapter? = null
    private var offlineOrEmpty = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_news, container, false)
        list = v.findViewById(R.id.news_list)
        filterRow = v.findViewById(R.id.filter_row)
        empty = v.findViewById(R.id.news_empty)
        pricePanel = v.findViewById(R.id.price_panel)
        priceRow = v.findViewById(R.id.price_row)
        pricePanel.visibility = View.GONE
        refresher = v.findViewById(R.id.news_refresh)
        refresher.setColorSchemeColors(0xFF58A6FF.toInt(), 0xFF3fb950.toInt(), 0xFFf85149.toInt())
        refresher.setOnRefreshListener { manualRefresh() }
        v.findViewById<TextView>(R.id.news_refresh_btn).setOnClickListener { manualRefresh() }
        list.layoutManager = LinearLayoutManager(requireContext())
        v.findViewById<EditText>(R.id.search_input).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s.toString().trim(); apply() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        load()
        return v
    }

    /** 过滤明显打不开/非新闻的链接（域名首页、Google 加密跳转壳等） */
    private fun isBadNewsUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val u = url.trim()
        if (!u.startsWith("http")) return true
        if (u.contains("news.google.com") && u.contains("/rss/articles")) return true
        // 无路径的域名首页不算可打开的文章
        val afterScheme = u.substringAfter("://")
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return true            // 纯域名，无任何路径
        val path = afterScheme.substring(slash + 1)
        if (path.isBlank() || path == "#") return true // 域名根
        // 截断/残缺路径（如 .../datacenters--us-）
        val stripped = path.trimEnd('/')
        if (stripped.endsWith("--") || (stripped.endsWith("-") && !stripped.endsWith("-story"))) return true
        // 促销/优惠券页
        val lower = u.lowercase()
        if (lower.contains("/promo-code") || lower.contains("promo-code")) return true
        return false
    }

    private fun onMain(run: () -> Unit) {
        if (isAdded && activity != null) activity!!.runOnUiThread(run)
    }


    private fun openArticle(item: NewsItem) {
        if (item.url.isBlank()) {
            // 国外行情只到“关键字”，无原链接 —— 弹框显示关键字/英文原标题
            try {
                val zh = item.title
                val en = item.enTitle
                val body = ItemContent()
                body.title = zh
                body.en = en
                body.kw = item.keywordsText
                body.sum = item.summary
                showKeywordDialog(body)
            } catch (e: Exception) { Toast.makeText(requireContext(), "无链接", Toast.LENGTH_SHORT).show() }
            return
        }
        ArticleActivity.open(requireContext(), item.title, item.url, item.summary)
    }

    /** 关键字内容载体 */
    class ItemContent { var title = ""; var en = ""; var kw = ""; var sum = "" }

    private fun showKeywordDialog(c: ItemContent) {
        val b = android.app.AlertDialog.Builder(requireContext())
        b.setTitle(c.title)
        val sb = StringBuilder()
        if (c.kw.isNotBlank()) sb.append("关键字：").append(c.kw).append("\n\n")
        if (c.en.isNotBlank() && c.en != c.title) sb.append("原文标题：").append(c.en).append("\n\n")
        if (c.sum.isNotBlank()) sb.append(c.sum)
        b.setMessage(if (sb.isNullOrEmpty()) { "已按关键字摘要收录，不含原文链接。" } else sb.toString())
        b.setPositiveButton("知道了", null)
        b.show()
    }
    /** 加载今日现货/期货报价 */
    private fun loadPrices() {
        Thread {
            try {
                val res = NewsClient.prices(requireContext())
                val obj = res.data as JSONObject
                val arr = obj.optJSONArray("prices")
                if (arr == null || arr.length() == 0) { onMain { pricePanel.visibility = View.GONE }; return@Thread }
                val items = mutableListOf<PriceItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    fun num(k: String): Double? {
                        return if (o.has(k) && !o.isNull(k)) { try { o.getDouble(k) } catch (e: Exception) { null } } else null
                    }
                    items.add(PriceItem(o.optString("symbol"), o.optString("name"),
                        o.optString("unit"), o.optDouble("change_pct", 0.0), o.optString("trend", "flat"),
                        o.optString("category"), o.optString("market"), o.optString("note"),
                        num("spot"), num("future"), num("basis"), num("basis_pct")))
                }
                val upd = obj.optString("updated_at", "")
                onMain { renderPriceRow(items, upd) }
            } catch (e: Exception) {
                onMain { pricePanel.visibility = View.GONE }
            }
        }.start()
    }

    private fun renderPriceRow(items: List<PriceItem>, updated: String) {
        if (items.isEmpty()) { pricePanel.visibility = View.GONE; return }
        priceRow.removeAllViews()
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        var lastGroup = ""
        for (it in items) {
            if (it.category != lastGroup) {
                lastGroup = it.category
                val sep = TextView(requireContext())
                sep.text = "‹ " + it.category + " ›"
                sep.setTextColor(0xFF58A6FF.toInt()); sep.textSize = 12f; sep.setTypeface(null, Typeface.BOLD)
                val sepLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                sepLp.setMarginStart(px(6)); sepLp.setMarginEnd(px(8)); sepLp.gravity = Gravity.CENTER_VERTICAL
                priceRow.addView(sep, sepLp)
            }
            val card = LinearLayout(requireContext())
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_price_card)
            val lp = LinearLayout.LayoutParams(px(132), LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = px(8)
            card.layoutParams = lp
            card.setPadding(px(10), px(8), px(10), px(8))

            val name = TextView(requireContext())
            name.text = if (it.market == "国内") it.name + "·国" else it.name
            name.setTextColor(0xFF6f6273.toInt()); name.textSize = 12f
            name.maxLines = 1
            card.addView(name)

            // 主价（现货或期货，现货优先当对标基准）
            val price = TextView(requireContext())
            price.text = it.priceText
            price.setTextColor(0xFF35293d.toInt()); price.textSize = 16f; price.setTypeface(null, Typeface.BOLD)
            price.setPadding(0, px(2), 0, 0)
            card.addView(price)

            // 现货 / 期货 双价比对
            if (it.hasBoth) {
                val row1 = TextView(requireContext())
                row1.text = "现货 " + it.fmt(it.spot!!)
                row1.setTextColor(0xFFd29922.toInt()); row1.textSize = 11f
                card.addView(row1)
                val row2 = TextView(requireContext())
                row2.text = "期货 " + it.fmt(it.future!!)
                row2.setTextColor(0xFF58a6ff.toInt()); row2.textSize = 11f
                card.addView(row2)
                val rowB = TextView(requireContext())
                rowB.text = "基差 " + it.basisText + " " + it.basisPctText
                rowB.setTextSize(11f)
                rowB.setTextColor(if (it.basis != null && it.basis!! > 0.001) Color.parseColor("#f85149") else 0xFF6f6273.toInt())
                rowB.setTypeface(null, Typeface.BOLD)
                card.addView(rowB)
            } else {
                val chg = TextView(requireContext())
                chg.text = it.chgText + " " + it.unit.take(4)
                chg.setTextSize(12f)
                chg.setTextColor(when {
                    it.changePct > 0.001 -> Color.parseColor("#f85149")
                    it.changePct < -0.001 -> Color.parseColor("#3fb950")
                    else -> 0xFF6f6273.toInt()
                })
                val lpl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lpl.width = px(104)
                card.addView(chg, lpl)
            }

            priceRow.addView(card)
        }
        pricePanel.visibility = View.VISIBLE
    }


    /** 手动刷新：真正触发云端抓取一遍，跑完再拉最新数据。 */
    private fun manualRefresh() {
        if (loading) return
        loading = true
        if (::refresher.isInitialized && !refresher.isRefreshing) refresher.isRefreshing = true
        renderLoading("正在云端抓取最新数据，约需1~3分钟…\n完成后会自动刷新")
        Thread {
            val res = CloudFetch.triggerAndWait()
            requireActivity().runOnUiThread {
                loading = false
                Toast.makeText(requireContext(), res.message, Toast.LENGTH_SHORT).show()
                load()
            }
        }.start()
    }

    private fun load() {
        if (loading) return
        loading = true
        if (::refresher.isInitialized && !refresher.isRefreshing) refresher.isRefreshing = true
        renderLoading("加载中…")
        Thread {
            try {
                val res = NewsClient.news(requireContext())
                val arr = res.data as JSONArray
                offlineOrEmpty = !res.live
                val news = mutableListOf<NewsItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tags = mutableListOf<String>()
                    val ta = o.optJSONArray("tags")
                    if (ta != null) for (j in 0 until ta.length()) tags.add(ta.optString(j))
                    val u = o.optString("url")
                    val kwOnly = tags.contains("关键字")
                    if (!kwOnly && isBadNewsUrl(u)) continue
                    val kws = mutableListOf<String>()
                    val ka = o.optJSONArray("keywords")
                    if (ka != null) for (j in 0 until ka.length()) kws.add(ka.optString(j))
                    news.add(NewsItem(o.optString("title"), u, o.optString("summary"),
                        o.optString("pub_time"), o.optString("source"), o.optInt("score", 0),
                        o.optString("priority"), o.optString("topic"), tags,
                        o.optBoolean("is_elite"), o.optString("dedup_key"), o.optJSONObject("breakdown"),
                        o.optString("en_title", o.optString("title")), kws))
                }
                // 新闻政策页：只展示国内财经/政策（国外行情在独立“国外”栏）
                val domestic = news.filter { it.tags.contains("国内") }.sortedWith(compareByDescending { it.pubTime.ifBlank { "\u0000" } })
                val live = res.live
                val newsTotal = news.size
                onMain {
                    NewsStore.current = domestic
                    all.clear(); all.addAll(domestic)
                    buildFilters(); apply()
                    if (domestic.isNotEmpty()) empty.visibility = View.GONE
                    else renderMessage(if (offlineOrEmpty) "离线快照中也暂无数据" else "暂无数据")
                }
                onMain { finishRefresh(true, live, newsTotal, domestic.size) }
            } catch (e: Exception) {
                onMain { renderError(); finishRefresh(false, false, 0, 0) }
            }
        }.start()
    }

    /** 结束刷新：停转圈 + 提示结果 */
    private fun finishRefresh(ok: Boolean, live: Boolean, newsTotal: Int = 0, domCount: Int = 0) {
        loading = false
        if (::refresher.isInitialized) refresher.isRefreshing = false
        // 记录到本地刷新日志
        val ctx = context
        if (ctx != null) {
            RefreshLog.record(ctx, "新闻", ok, live, newsTotal, 0, "")
        }
        val msg = when {
            !ok -> "刷新失败，已保留上次数据"
            live -> "已获取云端最新新闻 $newsTotal 条（国内 $domCount）"
            else -> "已加载离线快照 $domCount 条（联网后自动更新最新）"
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun renderLoading(msg: String) {
        empty.removeAllViews(); empty.visibility = View.VISIBLE
        val tv = TextView(requireContext())
        tv.text = msg
        tv.setTextColor(0xFF6f6273.toInt())
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 120, 0, 0)
        empty.addView(tv)
    }

    private fun renderError() {
        empty.removeAllViews(); empty.visibility = View.VISIBLE
        val wrap = LinearLayout(requireContext())
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER
        wrap.setPadding(40, 100, 40, 20)
        val tv = TextView(requireContext())
        tv.text = "数据加载失败\n请检查网络后重试"
        tv.setTextColor(0xFF6f6273.toInt()); tv.textSize = 14f; tv.gravity = Gravity.CENTER
        wrap.addView(tv)
        val btn = Button(requireContext())
        btn.text = "重试"
        btn.setOnClickListener { load() }
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        blp.topMargin = 18; blp.gravity = Gravity.CENTER_HORIZONTAL
        wrap.addView(btn, blp)
        empty.addView(wrap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderMessage(msg: String) {
        empty.removeAllViews(); empty.visibility = View.VISIBLE
        val tv = TextView(requireContext())
        tv.text = msg
        tv.setTextColor(0xFF6f6273.toInt()); tv.gravity = Gravity.CENTER; tv.setPadding(0, 120, 0, 0)
        empty.addView(tv)
    }

    private fun buildFilters() {
        filterRow.removeAllViews()
        val options = mutableListOf("全部")
        options.addAll(all.map { it.topic }.filter { it.isNotBlank() }.distinct().sorted())
        if (all.any { it.tags.contains("中文") }) options.add("中文")   // 中文筛选
        if (all.any { it.isElite }) options.add("精英")
        for (opt in options) {
            val tv = TextView(requireContext())
            tv.text = opt
            tv.setTextColor(if (opt == tag) 0xFF58A6FF.toInt() else 0xFF6f6273.toInt())
            tv.setBackgroundResource(if (opt == tag) R.drawable.bg_tag_selected else R.drawable.bg_tag)
            tv.setPadding(18, 10, 18, 10)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = 10
            tv.layoutParams = lp
            tv.setOnClickListener { tag = opt; buildFilters(); apply() }
            filterRow.addView(tv)
        }
    }

    private fun apply() {
        shown.clear()
        val q = query.lowercase()
        for (it in all) {
            if (tag != "全部") {
                if (tag == "精英" && !it.isElite) continue
                if (tag == "中文" && !it.tags.contains("中文")) continue
                if (tag != "精英" && tag != "中文" && it.topic != tag) continue
            }
            if (q.isNotEmpty()) {
                val hay = (it.title + " " + it.summary + " " + it.source).lowercase()
                if (!hay.contains(q)) continue
            }
            shown.add(it)
        }
        adapter = NewsAdapter(shown,
            onToggleStar = { item ->
                val now = NewsStore.toggle(requireContext(), item.dedupKey)
                item.starred = now
                val pos = shown.indexOf(item); if (pos >= 0) adapter?.notifyItemChanged(pos)
                Toast.makeText(requireContext(), if (now) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
            },
            onClick = { item ->
                openArticle(item)
            })
        list.adapter = adapter
        when {
            shown.isNotEmpty() -> empty.visibility = View.GONE
            all.isEmpty() -> { /* keep loading/error view */ }
            else -> {
                val prefix = if (offlineOrEmpty) "离线快照\n" else ""
                renderMessage(prefix + "没有匹配的资讯")
            }
        }
    }
}
