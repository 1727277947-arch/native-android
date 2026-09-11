package com.newsradar.app.ui

import android.graphics.Color
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
import com.newsradar.app.data.RefreshLog
import com.newsradar.app.data.CloudFetch
import com.newsradar.app.data.PriceItem
import org.json.JSONArray
import org.json.JSONObject

/** 期货/现货 垂直链式分离行情页 */
class PricesFragment : Fragment() {
    private lateinit var container: LinearLayout
    private lateinit var refresher: SwipeRefreshLayout
    @Volatile private var loading = false

    class GuideRow(val name: String, val direct: Int, val label: String, val strength: Int, val reason: String,
                 val anchor: Double?, val tp: Double?, val sl: Double?, val support: Double?, val resist: Double?)

    class PredictRow(val name: String, val date: String, val todayOpen: Double, val prevClose: Double,
                     val gapPct: Double, val direction: Int, val label: String,
                     val predNext: Double, val predLow: Double, val predHigh: Double, val reason: String,
                     val night: Boolean,
                     val limitScore: Int, val board: String,
                     val symbol: String = "")

    class HfRow(val name: String, val rank: Int, val isToday: Boolean, val dirLabel: String,
                val mode: String, val dayRangePct: Double, val estMargin: Double,
                val hands100k: Int, val limitScore: Int,
                val anchor: Double, val tp: Double, val sl: Double, val exit: Double, val boardScore: Double,
                val symbol: String = "")

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
        addTitle("大宗商品 · 期货/现货比对")
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
        addTitle("大宗商品 · 期货/现货比对")
        if (::refresher.isInitialized && !refresher.isRefreshing) refresher.isRefreshing = true
        val tv = TextView(requireContext())
        tv.text = "加载中…"
        tv.setTextColor(0xFF6f6273.toInt()); tv.gravity = Gravity.CENTER; tv.setPadding(0, 80, 0, 0)
        container.addView(tv)
        Thread {
            try {
                val res = NewsClient.prices(requireContext())
                val obj = res.data as JSONObject
                val arr = obj.getJSONArray("prices")
                val items = mutableListOf<PriceItem>()
                val guides = mutableListOf<GuideRow>()
                val predictions = mutableListOf<PredictRow>()
                val parr = obj.optJSONArray("predictions")
                if (parr != null) {
                    for (i in 0 until parr.length()) {
                        val q = parr.optJSONObject(i) ?: continue
                        predictions.add(PredictRow(
                            q.optString("name"), q.optString("date"), q.optDouble("today_open", 0.0),
                            q.optDouble("prev_close", 0.0), q.optDouble("gap_pct", 0.0),
                            q.optInt("direction", 0), q.optString("label", "观望"),
                            q.optDouble("pred_next_open", 0.0), q.optDouble("pred_low", 0.0),
                            q.optDouble("pred_high", 0.0), q.optString("reason", ""),
                            q.optBoolean("has_night", false), q.optInt("limit_score", 0),
                            q.optString("board", "一般/观望"), q.optString("symbol", "")))
                    }
                }
                predictions.sortWith(Comparator { a, b -> b.limitScore - a.limitScore })
                val hfPicks = mutableListOf<HfRow>()
                val harr = obj.optJSONArray("hf_picks")
                if (harr != null) {
                    for (i in 0 until harr.length()) {
                        val q = harr.optJSONObject(i) ?: continue
                        hfPicks.add(HfRow(
                            q.optString("name"), q.optInt("rank", 0), q.optBoolean("is_today", false),
                            q.optString("dir_label", "观望"), q.optString("mode", "打板"),
                            q.optDouble("day_range_pct", 0.0), q.optDouble("est_margin", 0.0),
                            q.optInt("hands_in_100k", 0), q.optInt("limit_score", 0),
                            q.optDouble("anchor", 0.0), q.optDouble("tp", 0.0), q.optDouble("sl", 0.0),
                            q.optDouble("exit_price", 0.0), q.optDouble("board_score", 0.0),
                            q.optString("symbol", "")))
                    }
                }
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(PriceItem(
                        o.optString("symbol"), o.optString("name"), o.optString("unit"),
                        o.optDouble("change_pct", 0.0), o.optString("trend", "flat"),
                        o.optString("category", "综合"), o.optString("market", "国际"),
                        o.optString("note", ""),
                        if (o.isNull("spot")) null else o.optDouble("spot", 0.0),
                        if (o.isNull("future")) null else o.optDouble("future", 0.0),
                        if (o.isNull("basis")) null else o.optDouble("basis", 0.0),
                        if (o.isNull("basis_pct")) null else o.optDouble("basis_pct", 0.0),
                        if (o.isNull("board_up")) null else o.optDouble("board_up", 0.0),
                        if (o.isNull("board_down")) null else o.optDouble("board_down", 0.0),
                        o.optJSONObject("guide")?.optString("label", "") ?: "",
                        o.optJSONObject("guide")?.optString("action", "观望") ?: "观望",
                        o.optJSONObject("guide")?.takeIf { !it.isNull("entry") }?.optDouble("entry", 0.0),
                        o.optJSONObject("guide")?.takeIf { !it.isNull("tp") }?.optDouble("tp", 0.0),
                        o.optJSONObject("guide")?.takeIf { !it.isNull("sl") }?.optDouble("sl", 0.0),
                        o.optJSONObject("guide")?.takeIf { !it.isNull("support") }?.optDouble("support", 0.0),
                        o.optJSONObject("guide")?.takeIf { !it.isNull("resist") }?.optDouble("resist", 0.0),
                        o.optJSONObject("guide")?.optString("reason", "") ?: "",
                        o.optJSONObject("guide")?.optInt("rr", 0) ?: 0,
                        o.optJSONObject("guide")?.takeIf { !it.isNull("risk_pct") }?.optDouble("risk_pct", 0.0),
                        o.optJSONObject("guide")?.optJSONObject("pyramid")?.optString("note", "") ?: "",
                        o.optJSONObject("guide")?.optString("move_stop", "") ?: "",
                        o.optJSONObject("guide")?.takeIf { !it.isNull("trail_stop") }?.optDouble("trail_stop", 0.0),
                        o.optJSONObject("guide")?.takeIf { !it.isNull("exit_price") }?.optDouble("exit_price", 0.0),
                        o.optJSONObject("guide")?.optString("stop_discipline", "") ?: "",
                        o.optDouble("est_margin", 0.0), o.optDouble("day_range_pct", 0.0)
                    ))
                    val go = o.optJSONObject("guide")
                    if (go != null) {
                        guides.add(GuideRow(
                            o.optString("name"), go.optInt("direct", 0),
                            go.optString("label", "观望"), go.optInt("strength", 0),
                            go.optString("reason", ""),
                            if (go.isNull("anchor")) null else go.optDouble("anchor", 0.0),
                            if (go.isNull("tp")) null else go.optDouble("tp", 0.0),
                            if (go.isNull("sl")) null else go.optDouble("sl", 0.0),
                            if (go.isNull("support")) null else go.optDouble("support", 0.0),
                            if (go.isNull("resist")) null else go.optDouble("resist", 0.0)))
                    }
                }
                val upd = obj.optString("updated_at", "")
                val mor = obj.optJSONObject("daily_pick")
                val aft = obj.optJSONObject("afternoon_pick")
                val live = res.live
                val priceTotal = items.size
                onMain {
                    try {
                        render(items, upd, guides, predictions, hfPicks, mor, aft)
                        finishRefresh(true, live, priceTotal, upd)
                    } catch (e: Exception) {
                        // 渲染异常兜底：不让行情页把整个App搞崩，给出可读提示
                        renderError(e.message ?: "render failed")
                        finishRefresh(false, false, 0, upd)
                    }
                }
            } catch (e: Exception) {
                onMain { renderError(e.message ?: "load failed"); finishRefresh(false, false, 0, "") }
            }
        }.start()
    }

    private fun onMain(run: () -> Unit) {
        if (isAdded && activity != null) activity!!.runOnUiThread(run)
    }

    /** 结束刷新：停转圈 + 提示结果 */
    private fun finishRefresh(ok: Boolean, live: Boolean, priceCount: Int = 0, updated: String = "") {
        loading = false
        if (::refresher.isInitialized) refresher.isRefreshing = false
        val ctx = context
        if (ctx != null) {
            RefreshLog.record(ctx, "行情", ok, live, 0, priceCount, updated)
        }
        val chg = if (updated.isNotBlank()) " · 更新 " + updated.take(16) else ""
        val msg = when {
            !ok -> "刷新失败，已保留上次数据"
            live -> "已获取云端最新行情 $priceCount 条（源更新见顶部）" + chg
            else -> "已加载离线快照 $priceCount 条（联网后自动更新最新）"
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun renderError(msg: String = "") {
        container.removeAllViews()
        addTitle("大宗商品 · 期货/现货比对")
        val tv = TextView(requireContext())
        tv.text = "数据加载失败，已保留上次数据\n请检查网络后重试" + (if (msg.isNotBlank()) "\n" + msg else "")
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

    private fun render(items: List<PriceItem>, updated: String, guides: List<GuideRow>, predictions: List<PredictRow> = emptyList(), hfPicks: List<HfRow> = emptyList(), morning: JSONObject? = null, afternoon: JSONObject? = null) {
        container.removeAllViews()
        addTitle("大宗商品 · 期货/现货比对")

        // —— 置顶核心：晨板 + 午板 两张卡，独立 ——
        renderBoardPicks(morning, afternoon)

        // —— 其余内容全部按“品种”汇总：一个品种只在它卡片里出现一次，集齐 现价/昨结/现货/基差/真板价/次日预测/打板分 ——
        if (updated.isNotBlank()) {
            val up = TextView(requireContext())
            up.text = "数据更新 " + updated
            up.setTextColor(0xFF6f6273.toInt()); up.textSize = 11f
            up.setPadding(dp(16), dp(6), dp(16), dp(2))
            container.addView(up)
        }
        renderAllBySymbol(items, predictions, hfPicks)
    }

    /** 把预测/打板/指南合并到各品种卡片上；一个品种只显示一次 */
    private fun renderAllBySymbol(items: List<PriceItem>, predictions: List<PredictRow>, hfPicks: List<HfRow>) {
        val predBy = HashMap<String, PredictRow>()
        for (p in predictions) {
            if (p.symbol.isEmpty()) continue
            val key = p.symbol.removePrefix("nf_")
            if (!predBy.containsKey(key)) predBy[key] = p
            if (p.symbol != key && !predBy.containsKey(p.symbol)) predBy[p.symbol] = p
        }
        val hfTop = HashMap<String, HfRow>()
        for (h in hfPicks) {
            // keep only the single real main pick; other rows must not offer entries
            if (h.isToday && h.symbol.isNotEmpty()) {
                val key = h.symbol.removePrefix("nf_")
                if (!hfTop.containsKey(key)) hfTop[key] = h
            }
        }
        // 国内期货品种：不分板块，一张表统一按“钱少(一手保证金低)+波动大优先”从前往后排
        val domestic = items.filter { it.future != null && it.market == "国内" }
        val cheapFirst = domestic.sortedWith(
            compareBy<PriceItem> { it.estMargin }.thenByDescending { it.dayRange })
        for (it in cheapFirst) {
            container.addView(mergedItemCard(it, predBy[it.symbol], hfTop[it.symbol]))
        }
        // 其余(纯现货/国际无期货品种)放末尾补充，避免丢品种
        val others = items.filter { !(it.future != null && it.market == "国内") }
        if (others.isNotEmpty()) {
            val sep = TextView(requireContext())
            sep.text = "—— 纯现货 / 国际 ——"
            sep.setTextColor(0xFF58a6ff.toInt()); sep.textSize = 11f; sep.setTypeface(null, Typeface.BOLD)
            sep.setPadding(dp(18), dp(14), dp(16), dp(2))
            container.addView(sep)
            for (it in others) container.addView(priceRow(it, it.future != null))
        }
        val tip = TextView(requireContext())
        tip.text = "保证金/手数按交易所公开乘数与保证金率估算，下单以最近一张结算与券商冻结为准；仅供参考"
        tip.setTextColor(0xFF5f677a.toInt()); tip.textSize = 11f
        tip.setPadding(dp(16), dp(8), dp(16), dp(4))
        container.addView(tip)
    }

    /** 一个品种一张卡：原有行情+策略，并叠加 次日预测与打板推荐行 */
    private fun mergedItemCard(it: PriceItem, pred: PredictRow?, hf: HfRow?): View {
        val card = priceRow(it, true)
        if (card !is LinearLayout) return card
        val sub = LinearLayout(requireContext())
        sub.orientation = LinearLayout.VERTICAL
        sub.setPadding(dp(2), dp(2), 0, 0)
        if (it.estMargin > 0) {
            val g0 = TextView(requireContext())
            g0.text = "一手保证金≈" + fmtNum(it.estMargin) + "元 · 10万约 " + it.hands100k + " 手（波动 " + "%.2f".format(it.dayRange) + "%）"
            g0.setTextColor(0xFF6f6273.toInt()); g0.textSize = 11f; g0.setPadding(0, dp(3), 0, 0)
            sub.addView(g0)
        }
        if (pred != null) {
            val l1 = TextView(requireContext())
            val gapTxt = if (pred.gapPct > 0) "+%.2f%%".format(pred.gapPct) else "%.2f%%".format(pred.gapPct)
            l1.text = "次日预测 " + pred.label + " " + gapTxt +
                    "  今开" + fmtNum(pred.todayOpen) + " vs 昨结" + fmtNum(pred.prevClose) +
                    "  →  预测开盘" + fmtNum(pred.predNext) + "  [" + fmtNum(pred.predLow) + "～" + fmtNum(pred.predHigh) + "]"
            l1.setTextColor(0xFFd29922.toInt()); l1.textSize = 12f
            l1.setPadding(0, dp(3), 0, 0)
            sub.addView(l1)
            val l2 = TextView(requireContext())
            l2.text = "  打板分" + pred.limitScore + " " + pred.board
            l2.setTextColor(0xFF6f6273.toInt()); l2.textSize = 11f; l2.setPadding(0, dp(1), 0, 0)
            sub.addView(l2)
        }
        if (hf != null) {
            val l3 = TextView(requireContext())
            l3.text = "主推 · " + hf.dirLabel + " 进场" + fmtNum(hf.anchor) +
                    " →止盈" + fmtNum(hf.tp) + " 硬止损" + fmtNum(hf.sl) + "  约" + hf.hands100k + "手/10万"
            l3.setTextColor(if (hf.dirLabel == "做多") 0xFFf85149.toInt() else 0xFF3fb950.toInt())
            l3.textSize = 12f; l3.setTypeface(null, Typeface.BOLD); l3.setPadding(0, dp(2), 0, 0)
            sub.addView(l3)
        }
        card.addView(sub)
        return card
    }

    private fun renderSpotsMinimal(items: List<PriceItem>, out: LinearLayout) {
        renderChainedGroup(items, isFuture = false, out = out)
        renderChainedGroup(items, isFuture = false, out = out)
    }

    private fun addSectionHeader(text: String) {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(0xFF58A6FF.toInt()); tv.textSize = 15f; tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(dp(16), dp(20), dp(16), dp(8))
        container.addView(tv)
    }

    private fun addEmptyRow(msg: String) {
        val tv = TextView(requireContext())
        tv.text = msg
        tv.setTextColor(0xFF6f6273.toInt()); tv.textSize = 13f
        tv.setPadding(dp(16), dp(4), dp(16), dp(10))
        container.addView(tv)
    }

    /** 高频交易推荐：小资金 + 高波动 + 活跃（十万内可频繁交易） */
    /** 今日打板 · 每天只做一次：波幅优先 + 方向强 + 资金小，给具体进场/止盈/止损位 */
    private fun renderHfPicks(picks: List<HfRow>, out: LinearLayout = container) {
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(8), dp(12), 0)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))

        val head = TextView(requireContext())
        head.text = "今日打板 · 每天只做一次"
        head.setTextColor(0xFF35293d.toInt()); head.textSize = 16f; head.setTypeface(null, Typeface.BOLD)
        card.addView(head)
        val sub = TextView(requireContext())
        sub.text = "按「日内波幅大 + 方向强 + 十万内资金宽裕」排序；第一名为今日主推，给具体进出场位"
        sub.setTextColor(0xFF5f677a.toInt()); sub.textSize = 11f
        sub.setPadding(0, dp(2), 0, dp(8))
        card.addView(sub)

        for (i in 0 until minOf(12, picks.size)) {
            val h = picks[i]
            val row = LinearLayout(requireContext())
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, dp(6), 0, 0)

            val top = LinearLayout(requireContext())
            top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
            val nm = TextView(requireContext())
            val lead = if (h.isToday) "★今日主推 " else ("#" + h.rank + " ")
            nm.text = lead + h.name
            nm.setTextColor(if (h.isToday) 0xFFf85149.toInt() else 0xFF35293d.toInt())
            nm.textSize = if (h.isToday) 15f else 14f; nm.setTypeface(null, Typeface.BOLD)
            nm.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            top.addView(nm)

            val dirColor = if (h.dirLabel == "做多") 0xFFf85149.toInt() else 0xFF3fb950.toInt()
            val dir = TextView(requireContext())
            dir.text = h.dirLabel + "(" + h.mode + ") · " + h.hands100k + "手/10万"
            dir.setTextColor(dirColor); dir.textSize = 12f; dir.setTypeface(null, Typeface.BOLD)
            top.addView(dir)
            row.addView(top)

            val info = TextView(requireContext())
            info.text = "波幅 " + "%.2f%%".format(h.dayRangePct) + " · 打板分" + h.limitScore + " · 一手保证金≈" + fmtNum(h.estMargin) +
                        "元 · 契合度 " + "%.2f".format(h.boardScore)
            info.setTextColor(0xFF6f6273.toInt()); info.textSize = 11f
            info.setPadding(0, dp(2), 0, dp(1))
            row.addView(info)

            val lv = TextView(requireContext())
            val arrow = if (h.dirLabel == "做多") "▲" else "▼"
            lv.text = "进场 " + fmtNum(h.anchor) + "  →  止盈 " + fmtNum(h.tp) + "  |  反向 " +
                      fmtNum(h.exit) + " 离场 / 硬止损 " + fmtNum(h.sl)
            lv.setTextColor(0xFF58a6ff.toInt()); lv.textSize = 12f; lv.setTypeface(null, Typeface.BOLD)
            lv.setPadding(0, dp(1), 0, 8)
            row.addView(lv)
            card.addView(row)
        }
        out.addView(card)

        val tip = TextView(requireContext())
        tip.text = "只打当日一次机会；严格止损，止盈+3%/反向-0.15%离场/浮盈回吐-0.1%硬止损；保证金按交易所公开值估算，下单以期货公司为准"
        tip.setTextColor(0xFF5f677a.toInt()); tip.textSize = 11f
        tip.setPadding(dp(16), dp(4), dp(16), dp(4))
        out.addView(tip)
    }

    /** 每日投资指南：做多/做空/观望（只做每日短线） */
    private fun renderGuide(guides: List<GuideRow>, out: LinearLayout = container) {
        val longs = guides.filter { it.direct > 0 }
        val shorts = guides.filter { it.direct < 0 }
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(16)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))

        val head = LinearLayout(requireContext())
        head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        val t1 = TextView(requireContext())
        t1.text = "每日投资指南"
        t1.setTextColor(0xFF35293d.toInt()); t1.textSize = 16f; t1.setTypeface(null, Typeface.BOLD)
        val t1lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        t1.layoutParams = t1lp
        head.addView(t1)
        val summary = TextView(requireContext())
        summary.text = "做多 " + longs.size + "  /  做空 " + shorts.size + "  /  只做每日短线"
        summary.setTextColor(0xFF6f6273.toInt()); summary.textSize = 11f
        head.addView(summary)
        card.addView(head)

        val tip = TextView(requireContext())
        tip.text = "基于当日动量+现货基差综合判断，仅供参考，不构成投资建议"
        tip.setTextColor(0xFF5f677a.toInt()); tip.textSize = 11f
        tip.setPadding(0, dp(2), 0, dp(8))
        card.addView(tip)

        if (longs.isEmpty() && shorts.isEmpty()) {
            val e = TextView(requireContext())
            e.text = "今日无明确方向信号，建议观望"
            e.setTextColor(0xFF6f6273.toInt()); e.textSize = 13f; e.setPadding(0, dp(6), 0, 0)
            card.addView(e)
        } else {
            if (longs.isNotEmpty()) {
                card.addView(guideGroup("↑ 偏多", longs, true))
            }
            if (shorts.isNotEmpty()) {
                card.addView(guideGroup("↓ 偏空", shorts, false))
            }
        }
        out.addView(card)
    }

    private fun guideGroup(title: String, list: List<GuideRow>, long: Boolean): View {
        val col = LinearLayout(requireContext())
        col.orientation = LinearLayout.VERTICAL
        val st = TextView(requireContext())
        st.text = title
        st.setTextColor(if (long) 0xFF3fb950.toInt() else 0xFFf85149.toInt())
        st.textSize = 13f; st.setTypeface(null, Typeface.BOLD)
        st.setPadding(0, dp(8), 0, dp(2))
        col.addView(st)
        for (r in list) {
            val row = LinearLayout(requireContext())
            row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(4), 0, 0)
            val nm = TextView(requireContext())
            nm.text = r.name
            nm.setTextColor(0xFF35293d.toInt()); nm.textSize = 13f; nm.setTypeface(null, Typeface.BOLD)
            val nmlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            nm.layoutParams = nmlp; nmlp.width = dp(88)
            row.addView(nm)
            val lb = TextView(requireContext())
            lb.text = r.label + (if (r.strength >= 3) "（强）" else "")
            lb.setTextColor(if (long) 0xFF3fb950.toInt() else 0xFFf85149.toInt())
            lb.textSize = 12f
            lb.setPadding(0, 0, dp(8), 0)
            row.addView(lb)
            val rs = TextView(requireContext())
            rs.text = r.reason
            rs.setTextColor(0xFF6f6273.toInt()); rs.textSize = 12f
            row.addView(rs)
            col.addView(row)
            // 具体做多/做空价位
            if (r.tp != null || r.sl != null || r.support != null || r.resist != null) {
                val lv = TextView(requireContext())
                val fmt = fun(v: Double): String =
                    if (v >= 1000) "%.0f".format(v) else "%.2f".format(v)
                var lvText = ""
                if (r.anchor != null) lvText += "参考" + fmt(r.anchor)
                if (r.tp != null) lvText += " · 止盈" + fmt(r.tp)
                if (r.sl != null) lvText += " · 止损" + fmt(r.sl)
                if (r.support != null) lvText += " · 支撑" + fmt(r.support)
                if (r.resist != null) lvText += " · 压力" + fmt(r.resist)
                lv.text = lvText
                lv.setTextColor(if (r.direct > 0) 0xFF3fb950.toInt() else if (r.direct < 0) 0xFFf85149.toInt() else 0xFFd29922.toInt())
                lv.textSize = 12f
                lv.setTypeface(null, Typeface.BOLD)
                lv.setPadding(dp(88), 0, 0, 0)
                col.addView(lv)
            }
        }
        return col
    }


    /** 次日开盘预测：今开 vs 昨结 -> 做多/做空关联 + 模型预测次日开盘 */
    /** 今日开盘策略 · 晨板+午后板 横卡 */
    private fun renderBoardPicks(morning: JSONObject?, afternoon: JSONObject?) {
        if (morning == null && afternoon == null) return
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(8), dp(12), 0)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        val head = TextView(requireContext())
        head.text = "今日开盘策略 · 上午板 + 午后板"
        head.setTextColor(0xFF35293d.toInt()); head.textSize = 16f; head.setTypeface(null, Typeface.BOLD)
        card.addView(head)
        val sub = TextView(requireContext())
        sub.text = "直接给进场/止盈/反向离场/硬止损与手数，开盘前照此下单即可"
        sub.setTextColor(0xFF5f677a.toInt()); sub.textSize = 11f; sub.setPadding(0, dp(2), 0, dp(6))
        card.addView(sub)
        var shown = false
        for ((tag, o) in listOf("晨盘打板" to morning, "午后打板" to afternoon)) {
            if (o == null || o.optDouble("anchor", 0.0) <= 0.0) continue
            shown = true
            val name = o.optString("name", "")
            val sy = o.optString("symbol", "")
            val head2 = TextView(requireContext())
            val pb = o.optString("plan_basis", "")
            val pbTxt = if (pb == "afternoon_last") "  · 按午后现价重算" else if (pb == "alt_symbol") "  · 换标的机会" else ""
            head2.text = "■ " + tag + "  " + name + "(" + sy + ")" + pbTxt
            head2.setTextColor(0xFF35293d.toInt()); head2.textSize = 14f; head2.setTypeface(null, Typeface.BOLD)
            head2.setPadding(0, dp(4), 0, 0)
            card.addView(head2)
            val dir = o.optString("dir_label", "观望")
            val color = if (dir == "做多") 0xFFf85149.toInt() else if (dir == "做空") 0xFF3fb950.toInt() else 0xFFd29922.toInt()
            val bias = o.optString("day_bias", "")
            val biasCn = when (bias) {
                "bull" -> "偏多"
                "bear" -> "偏空"
                "mix" -> "均线缠绕·观望"
                else -> ""
            }
            if (biasCn.isNotEmpty()) {
                val biasRow = TextView(requireContext())
                val maArr = o.optJSONArray("day_ma")
                var maTxt = ""
                if (maArr != null && maArr.length() >= 3) {
                    maTxt = "  MA5 " + "%.0f".format(maArr.optDouble(0)) +
                            " · MA20 " + "%.0f".format(maArr.optDouble(1)) +
                            " · MA60 " + "%.0f".format(maArr.optDouble(2))
                }
                biasRow.text = "日线" + biasCn + maTxt
                biasRow.setTextColor(color); biasRow.textSize = 11f
                biasRow.setPadding(0, dp(1), 0, 0)
                card.addView(biasRow)
            }

            // 开盘双轨：真实今开 real_open (東财f46) 之上 → 破位追强 / 回踩承接 / 跌破当日放弃（纯展示）
            if (o.has("real_open") && !o.isNull("real_open")) {
                val ro = o.optDouble("real_open", 0.0)
                val roTxt = if (ro > 0) "今开 " + boardNum(ro) else ""
                val du: Any? = o.opt("dual")
                var dz = roTxt
                var dcolor = 0xFFd29922.toInt()
                if (du is JSONObject) {
                    val st = du.optString("st", ""); val ref = du.optDouble("ref", 0.0)
                    val refT = if (ref > 0) boardNum(ref) else boardNum(if (ro > 0) ro else 0.0)
                    when (st) {
                        "break_a" -> { dz += if (dz.isEmpty()) "" else "  ·  " ; dz += "开盘上方走强：破今开追强确认，回踩" + refT + "承接"; dcolor = 0xFFf85149.toInt() }
                        "dip_b" -> { dz += if (dz.isEmpty()) "" else "  ·  "; dz += "回踩今开承接≈" + refT + "待企稳"; dcolor = 0xFF3fb950.toInt() }
                        "drop" -> { dz += if (dz.isEmpty()) "" else "  ·  "; dz += "已跌破今开转弱：当日放弃，勿追顶勿抄回踩"; dcolor = 0xFF6f6273.toInt() }
                        else -> { dz += "  ·  开盘双轨待定" }
                    }
                } else if (dz.isNotEmpty()) {
                    dz += "  ·  开盘双轨待竞价(约09:00见今开后复核)，勿按预估现价追高"
                }
                val dzRow = TextView(requireContext())
                dzRow.text = dz; dzRow.setTextColor(dcolor); dzRow.textSize = 11f
                dzRow.setPadding(0, dp(1), 0, 0)
                card.addView(dzRow)
            }

            val r1 = TextView(requireContext())
            var t = dir + "  进场≈" + boardNum(o.optDouble("anchor", 0.0))
            t += "  止盈" + boardNum(o.optDouble("tp", 0.0))
            t += "  硬止损" + boardNum(o.optDouble("sl", 0.0))
            val ex = o.optDouble("exit_price", 0.0)
            if (ex > 0.0) t += "  反向" + boardNum(ex) + "离场"
            r1.text = t
            r1.setTextColor(color); r1.textSize = 14f; r1.setTypeface(null, Typeface.BOLD); r1.setPadding(0, dp(2), 0, 0)
            card.addView(r1)
            val r2 = TextView(requireContext())
            r2.text = "一手保证金≈" + boardNum(o.optDouble("est_margin", 0.0)) + "元  ·  十万内约 " + o.optInt("hands_in_100k", 0) + " 手"
            r2.setTextColor(0xFF6f6273.toInt()); r2.textSize = 11f; r2.setPadding(0, dp(1), 0, 0)
            card.addView(r2)

            // 同品种多/空双方案（服务端 long_plan / short_plan，含 ATR 自适应止损，纯展示）
            val lp2: Any? = o.opt("long_plan")
            if (lp2 is JSONObject) {
                val row = TextView(requireContext())
                val slp = lp2.optDouble("sl_pct", 0.0)
                row.text = "多单  进场 " + boardNum(lp2.optDouble("entry", 0.0)) +
                        "  止损 " + boardNum(lp2.optDouble("sl", 0.0)) + "(-" + "%.2f".format(slp) + "%)" +
                        "  止盈 " + boardNum(lp2.optDouble("tp", 0.0))
                row.setTextColor(0xFFf85149.toInt()); row.textSize = 12f; row.setTypeface(null, Typeface.BOLD)
                row.setPadding(0, dp(3), 0, 0)
                card.addView(row)
                val note = lp2.optString("note", "")
                if (note.isNotEmpty()) {
                    val n = TextView(requireContext())
                    n.text = "  " + note
                    n.setTextColor(0xFF6f6273.toInt()); n.textSize = 10f
                    card.addView(n)
                }
            }
            val sp2: Any? = o.opt("short_plan")
            if (sp2 is JSONObject) {
                val row = TextView(requireContext())
                val slp = sp2.optDouble("sl_pct", 0.0)
                row.text = "空单  进场 " + boardNum(sp2.optDouble("entry", 0.0)) +
                        "  止损 " + boardNum(sp2.optDouble("sl", 0.0)) + "(+" + "%.2f".format(slp) + "%)" +
                        "  止盈 " + boardNum(sp2.optDouble("tp", 0.0))
                row.setTextColor(0xFF3fb950.toInt()); row.textSize = 12f; row.setTypeface(null, Typeface.BOLD)
                row.setPadding(0, dp(3), 0, 0)
                card.addView(row)
                val note = sp2.optString("note", "")
                if (note.isNotEmpty()) {
                    val n = TextView(requireContext())
                    n.text = "  " + note
                    n.setTextColor(0xFF6f6273.toInt()); n.textSize = 10f
                    card.addView(n)
                }
            }
        }
        if (!shown) {
            val none = TextView(requireContext())
            none.text = "今日暂无真实可打板的品种：无真实板价数据或已封板不可追，宁缺不硬推"
            none.setTextColor(0xFF6f6273.toInt()); none.textSize = 12f; none.setPadding(0, dp(4), 0, 0)
            card.addView(none)
        }
        container.addView(card)
    }
    private fun boardNum(v: Double): String = if (v == 0.0) "-" else if (v >= 1000) "%.0f".format(v) else "%.2f".format(v)

    private fun renderPredict(predictions: List<PredictRow>, out: LinearLayout = container) {
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(8), dp(12), 0)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))

        val head = TextView(requireContext())
        head.text = "期货主线 · 次日开盘预测/打板潜力"
        head.setTextColor(0xFF35293d.toInt()); head.textSize = 16f; head.setTypeface(null, Typeface.BOLD)
        card.addView(head)
        val sub = TextView(requireContext())
        sub.text = "打板分≥70视为疑似打板候选(强势追多)，已含夜盘实时价；高开偏多/低开偏空"
        sub.setTextColor(0xFF5f677a.toInt()); sub.textSize = 11f
        sub.setPadding(0, dp(2), 0, dp(8))
        card.addView(sub)

        for (p in predictions) {
            val col = LinearLayout(requireContext())
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(0, dp(6), 0, 0)
            val top = LinearLayout(requireContext())
            top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
            val nm = TextView(requireContext())
            nm.text = p.name
            nm.setTextColor(0xFF35293d.toInt()); nm.textSize = 14f; nm.setTypeface(null, Typeface.BOLD)
            nm.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            top.addView(nm)
            val badge = TextView(requireContext())
            val bCol = if (p.limitScore >= 70) 0xFFf85149.toInt() else if (p.limitScore >= 50) 0xFFd29922.toInt() else 0xFF5f677a.toInt()
            badge.text = "打板分" + p.limitScore + " " + p.board
            badge.setTextColor(bCol); badge.textSize = 11f; badge.setTypeface(null, Typeface.BOLD)
            badge.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) }
            top.addView(badge)
            val dirColor = if (p.direction > 0) 0xFFf85149.toInt() else if (p.direction < 0) 0xFF3fb950.toInt() else 0xFFd29922.toInt()
            val dirstr = TextView(requireContext())
            val gapTxt = if (p.gapPct > 0) "+%.2f%%".format(p.gapPct) else "%.2f%%".format(p.gapPct)
            dirstr.text = p.label + (if (p.night) " · 夜盘" else "") + " · " + gapTxt
            dirstr.setTextColor(dirColor); dirstr.textSize = 12f; dirstr.setTypeface(null, Typeface.BOLD)
            top.addView(dirstr)
            col.addView(top)
            val pred = TextView(requireContext())
            pred.text = "今开 " + fmtNum(p.todayOpen) + "  vs  昨结参考 " + fmtNum(p.prevClose) + "   →   预测次日开盘 " + fmtNum(p.predNext)
            pred.setTextColor(0xFF58a6ff.toInt()); pred.textSize = 13f; pred.setTypeface(null, Typeface.BOLD)
            pred.setPadding(0, dp(2), 0, 0)
            col.addView(pred)
            val rng = TextView(requireContext())
            rng.text = "预测区间 " + fmtNum(p.predLow) + " ～ " + fmtNum(p.predHigh)
            rng.setTextColor(0xFF6f6273.toInt()); rng.textSize = 11f
            col.addView(rng)
            val rs = TextView(requireContext())
            rs.text = p.reason
            rs.setTextColor(0xFF5a4f66.toInt()); rs.textSize = 12f; rs.setLineSpacing(0f, 1.1f)
            rs.setPadding(0, dp(2), 0, 8)
            col.addView(rs)
            card.addView(col)
        }
        out.addView(card)

        val tip = TextView(requireContext())
        tip.text = "次日开盘以实际集合竞价为准；预测基于历史隔夜跳空统计+今日跳空惯性，仅供研究参考，不构成投资建议"
        tip.setTextColor(0xFF5f677a.toInt()); tip.textSize = 11f
        tip.setPadding(dp(16), dp(6), dp(16), dp(4))
        out.addView(tip)
    }

    private fun fmtNum(v: Double): String = if (v >= 1000) "%.0f".format(v) else if (v >= 1) "%.2f".format(v) else "%.4f".format(v)

    private fun renderChainedGroup(items: List<PriceItem>, isFuture: Boolean, out: LinearLayout = container) {
        val byCat = LinkedHashMap<String, MutableList<PriceItem>>()
        for (it in items) byCat.getOrPut(it.category) { mutableListOf() }.add(it)
        for ((cat, list) in byCat) {
            val sep = TextView(requireContext())
            sep.text = "‹ " + cat + " ›"
            sep.setTextColor(0xFF3fb950.toInt()); sep.textSize = 12f; sep.setTypeface(null, Typeface.BOLD)
            sep.setPadding(dp(18), dp(12), dp(16), dp(4))
            out.addView(sep)
            for (it in list) out.addView(priceRow(it, isFuture))
        }
    }

    private fun priceRow(it: PriceItem, isFuture: Boolean): View {
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(8), dp(12), 0)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(10), dp(14), dp(10))

        val head = LinearLayout(requireContext())
        head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        val name = TextView(requireContext())
        name.text = nameText(it)
        name.setTextColor(0xFF35293d.toInt()); name.textSize = 15f; name.setTypeface(null, Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(name)
        val chg = TextView(requireContext())
        val c = it.changePct
        chg.text = when {
            c > 0.001 -> "+%.2f%%".format(c)
            c < -0.001 -> "%.2f%%".format(c)
            else -> "持平"
        }
        chg.textSize = 13f
        chg.setTextColor(when {
            c > 0.001 -> Color.parseColor("#f85149")
            c < -0.001 -> Color.parseColor("#3fb950")
            else -> 0xFF6f6273.toInt()
        })
        chg.setTypeface(null, Typeface.BOLD)
        head.addView(chg)
        card.addView(head)

        val price = if (isFuture) it.future else it.spot
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(4), 0, 0)
        val curLab = TextView(requireContext())
        curLab.text = if (isFuture) "期货" else "现货"
        curLab.setTextColor(0xFF6f6273.toInt()); curLab.textSize = 11f
        row.addView(curLab)
        val curVal = TextView(requireContext())
        curVal.text = it.fmt(price!!)
        curVal.setTextColor(if (isFuture) 0xFF58a6ff.toInt() else 0xFFd29922.toInt())
        curVal.textSize = 18f; curVal.setTypeface(null, Typeface.BOLD); curVal.setPadding(dp(6), 0, 0, 0)
        row.addView(curVal)
        val unit = TextView(requireContext())
        unit.text = " " + it.unit
        unit.setTextColor(0xFF6f6273.toInt()); unit.textSize = 11f
        row.addView(unit)
        card.addView(row)

        if (it.boardUp != null || it.boardDown != null) {
            val brd = TextView(requireContext())
            val upT = if (it.boardUp != null) "涨停 " + it.fmt(it.boardUp!!) else ""
            val dnT = if (it.boardDown != null) "跌停 " + it.fmt(it.boardDown!!) else ""
            brd.text = "今日真实板价  " + listOf(upT, dnT).filter { it.isNotBlank() }.joinToString("  /  ")
            brd.setTextColor(if (isFuture) 0xFFf85149.toInt() else 0xFF3fb950.toInt())
            brd.textSize = 12f; brd.setTypeface(null, Typeface.BOLD)
            brd.setPadding(dp(2), dp(4), 0, 0)
            card.addView(brd)
        }

        if (it.hasBoth && isFuture) {
            val spotAbs = TextView(requireContext())
            spotAbs.text = "现货 " + it.fmt(it.spot!!) + " " + it.unit
            spotAbs.setTextColor(0xFFd29922.toInt()); spotAbs.textSize = 14f; spotAbs.setTypeface(null, Typeface.BOLD)
            spotAbs.setPadding(dp(2), dp(2), 0, 0)
            card.addView(spotAbs)
        }
        if (it.hasBoth) {            val cmp = TextView(requireContext())
            val diff = (it.future!! - it.spot!!)
            cmp.text = "基差(期货-现货) " + (if (diff >= 0) "+" else "") + it.fmt(diff) + "  " + it.basisPctText
            cmp.textSize = 12f
            cmp.setTextColor(if (diff > 0.001) Color.parseColor("#f85149") else if (diff < -0.001) Color.parseColor("#3fb950") else 0xFF6f6273.toInt())
            cmp.setPadding(0, dp(2), 0, 0)
            card.addView(cmp)
        }

        val note = TextView(requireContext())
        note.text = it.note.ifBlank { "国内报价" }
        note.setTextColor(0xFF5f677a.toInt()); note.textSize = 11f
        note.setPadding(0, dp(2), 0, 0)
        card.addView(note)
        addStrategyRow(card, it)
        return card
    }

    private fun addStrategyRow(card: LinearLayout, it: PriceItem) {
        if (it.guideReason.isBlank() && it.guideEntry == null && it.guideTp == null
            && it.guideSl == null && it.guideSupport == null && it.guideResist == null) return
        val color = when (it.guideLabel) {
            "做多" -> 0xFFf85149.toInt()
            "做空" -> 0xFF3fb950.toInt()
            else -> 0xFFd29922.toInt()
        }
        val fmt = fun(v: Double): String = if (v >= 100) "%.0f".format(v) else "%.2f".format(v)
        var txt = it.guideReason
        if (it.guideEntry != null) txt += "  |  开仓 " + fmt(it.guideEntry!!)
        if (it.guideTp != null) txt += "  止盈 " + fmt(it.guideTp!!)
        if (it.guideSl != null) txt += "  止损 " + fmt(it.guideSl!!)
        if (it.guideSupport != null) txt += "  支撑 " + fmt(it.guideSupport!!)
        if (it.guideResist != null) txt += "  压力 " + fmt(it.guideResist!!)
        val sepHdr = LinearLayout(requireContext())
        sepHdr.orientation = LinearLayout.HORIZONTAL; sepHdr.gravity = Gravity.CENTER_VERTICAL
        sepHdr.setPadding(0, dp(6), 0, 0)
        val tag = TextView(requireContext())
        tag.text = "购买策略"
        tag.setTextColor(color); tag.textSize = 11f; tag.setTypeface(null, Typeface.BOLD)
        sepHdr.addView(tag)
        val act = TextView(requireContext())
        act.text = it.guideAction.ifBlank { it.guideLabel }
        act.setTextColor(color); act.textSize = 11f; act.setTypeface(null, Typeface.BOLD)
        act.setPadding(dp(6), 0, 0, 0)
        sepHdr.addView(act)
        card.addView(sepHdr)
        val st = TextView(requireContext())
        st.text = txt
        st.setTextColor(color); st.textSize = 12f
        st.setPadding(0, dp(2), 0, 0)
        card.addView(st)
        if (it.guideRr > 0) {
            val dis = TextView(requireContext())
            var d = "盈亏比 1:" + it.guideRr
            if (it.guideRisk != null) d += "  ·  单次风险≤1/3资本(止损幅度 " + ("%.1f".format(it.guideRisk!!)) + "%)"
            dis.text = d
            dis.setTextColor(color); dis.textSize = 11f
            dis.setPadding(0, dp(3), 0, 0)
            card.addView(dis)
        }
        if (it.guidePyramid.isNotBlank()) {
            val py = TextView(requireContext())
            py.text = "金字塔加仓: " + it.guidePyramid
            py.setTextColor(0xFF6f6273.toInt()); py.textSize = 11f
            py.setPadding(0, dp(2), 0, 0)
            card.addView(py)
        }
        if (it.guideMoveStop.isNotBlank()) {
            val ms = TextView(requireContext())
            ms.text = it.guideMoveStop
            ms.setTextColor(0xFFd29922.toInt()); ms.textSize = 11f
            ms.setPadding(0, dp(2), 0, 0)
            card.addView(ms)
        }
        if (it.guideTrailStop != null) {
            val ts = TextView(requireContext())
            ts.text = ("反向离场/止损线: " + (if (it.guideTrailStop!! >= 100) "%.0f".format(it.guideTrailStop!!) else "%.2f".format(it.guideTrailStop!!)) + "（移动止损参考）")
            ts.setTextColor(0xFFf85149.toInt()); ts.textSize = 11f; ts.setTypeface(null, Typeface.BOLD)
            ts.setPadding(0, dp(2), 0, 0)
            card.addView(ts)
        }
        if (it.guideStopDiscipline.isNotBlank()) {
            val sd = TextView(requireContext())
            sd.text = it.guideStopDiscipline
            sd.setTextColor(0xFFf85149.toInt()); sd.textSize = 11f
            sd.setPadding(0, dp(2), 0, 0)
            card.addView(sd)
        }
    }

    private fun nameText(it: PriceItem): String =
        if (it.market == "国内") it.name + "·国" else it.name

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}


