package com.newsradar.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.newsradar.app.Auth
import com.newsradar.app.data.RefreshLog
import com.newsradar.app.data.ScheduleInfo
import com.newsradar.app.R

/** 我的/登录 页 */
class AccountFragment : Fragment() {
    private lateinit var container: LinearLayout
    private var passInput: EditText? = null
    private var error: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        val root = NestedScrollView(requireContext())
        root.setBackgroundResource(R.color.bg)
        root.isFillViewport = true
        val col = LinearLayout(requireContext())
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(28), dp(64), dp(28), dp(40))
        root.addView(col, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        this.container = col
        return root
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        container.removeAllViews()
        passInput = null; error = null

        val logo = TextView(requireContext())
        logo.text = "\ud83d\udce1 心得"
        logo.setTextColor(0xFF3a2c4a.toInt()); logo.textSize = 30f; logo.setTypeface(null, Typeface.BOLD)
        logo.gravity = Gravity.CENTER
        container.addView(logo)

        if (Auth.isLoggedIn(requireContext())) {
            val sub = TextView(requireContext())
            sub.text = "当前登录：" + Auth.USER
            sub.setTextColor(0xFF7b6f86.toInt()); sub.textSize = 14f
            sub.gravity = Gravity.CENTER; sub.setPadding(0, dp(20), 0, 0)
            container.addView(sub)
            val ok = TextView(requireContext())
            ok.text = "\u2713 已登录"
            ok.setTextColor(0xFF3fb950.toInt()); ok.textSize = 16f; ok.gravity = Gravity.CENTER
            ok.setPadding(0, dp(8), 0, 0)
            container.addView(ok)
            val logout = Button(requireContext())
            logout.text = "退出登录"
            val ll = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            ll.topMargin = dp(28); logout.layoutParams = ll; logout.minHeight = dp(48)
            logout.setOnClickListener { Auth.logout(requireContext()); render() }
            container.addView(logout)
            addRefreshJournal()
            return
        }

        val sub = TextView(requireContext())
        sub.text = "请输入访问密码进入会员版"
        sub.setTextColor(0xFF7b6f86.toInt()); sub.textSize = 13f
        sub.gravity = Gravity.CENTER; sub.setPadding(0, dp(12), 0, 0)
        container.addView(sub)

        passInput = EditText(requireContext()).apply {
            hint = "访问密码"
            setTextColor(0xFF3a2c4a.toInt())
            setHintTextColor(0xFF7b6f86.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundResource(R.drawable.bg_price_card)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(32)
            layoutParams = lp
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        container.addView(passInput)

        val btn = Button(requireContext())
        btn.text = "登录"
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        blp.topMargin = dp(24); btn.layoutParams = blp; btn.minHeight = dp(48)
        btn.setOnClickListener { doLogin() }
        container.addView(btn)

        error = TextView(requireContext()).apply {
            setTextColor(0xFFFF6B7F.toInt()); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            minHeight = dp(22)
        }
        container.addView(error)
        addRefreshJournal()
    }

    /** 显示最近刷新记录（本地日志，反映每次手动/自动刷新效果） */
    private fun addRefreshJournal() {
        addSyncSchedule()
        val sep = TextView(requireContext())
        sep.text = "· 运行日志 ·"
        sep.setTextColor(0xFF7b6f86.toInt()); sep.textSize = 13f; sep.setTypeface(null, Typeface.BOLD)
        sep.gravity = Gravity.LEFT; sep.setPadding(0, dp(34), 0, dp(6))
        container.addView(sep)

        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_price_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        card.layoutParams = lp
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        container.addView(card)

        val arr = RefreshLog.list(requireContext())
        if (arr.length() == 0) {
            val tv = TextView(requireContext())
            tv.text = "暂无刷新记录 · 下拉列表或点刷新即可看到效果"
            tv.setTextColor(0xFF7b6f86.toInt()); tv.textSize = 13f
            card.addView(tv)
            return
        }
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            val time = o.optString("time", "")
            val kind = o.optString("kind", "")
            val ok = o.optBoolean("ok", false)
            val live = o.optBoolean("live", false)
            val news = o.optInt("news", 0)
            val prices = o.optInt("prices", 0)
            val updated = o.optString("updated", "")
            val tv = TextView(requireContext())
            val sb = StringBuilder()
            sb.append(time).append("  ").append(kind).append("：")
            if (!ok) sb.append("失败，保留上次数据")
            else if (news > 0) sb.append("新闻 ").append(news).append(" 条")
            else if (prices > 0) sb.append("行情 ").append(prices).append(" 条")
            else sb.append(if (live) "已更新" else "离线快照")
            if (updated.isNotBlank()) sb.append(" · 更新 ").append(updated.take(16))
            tv.text = sb.toString()
            tv.setTextColor(if (ok) 0xFF3fb950.toInt() else 0xFFFF6B7F.toInt())
            tv.textSize = 13f
            tv.setPadding(0, dp(4), 0, 4)
            card.addView(tv)
        }
    }

    /** Automatic sync/push schedule hint (renders from ScheduleInfo lists). */
    private fun addSyncSchedule() {
        val sep = TextView(requireContext())
        sep.text = ScheduleInfo.header()
        sep.setTextColor(0xFF7b6f86.toInt()); sep.textSize = 13f
        sep.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        sep.setPadding(0, dp(28), 0, dp(6))
        container.addView(sep)
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(com.newsradar.app.R.drawable.bg_price_card)
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        val tv = TextView(requireContext())
        tv.text = ScheduleInfo.formattedBlock()
        tv.setTextColor(0xFF3a2c4a.toInt()); tv.textSize = 13f
        tv.setLineSpacing(dp(2).toFloat(), 1f)
        card.addView(tv)
        container.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun doLogin() {
        val msg = passInput?.text?.toString()?.trim() ?: ""
        if (Auth.checkPassword(msg)) {
            Auth.login(requireContext())
            Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
            render()
        } else {
            error?.text = "密码错误，请重新输入"
            passInput?.text?.clear()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
