package com.newsradar.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.newsradar.app.R

abstract class InfoFragment(private val title: String) : Fragment() {
    protected lateinit var content: LinearLayout
    protected lateinit var swipe: SwipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_list, container, false)
        v.findViewById<TextView>(R.id.header_title).text = title
        content = v.findViewById(R.id.content)
        swipe = v.findViewById(R.id.swipe)
        swipe.setColorSchemeResources(R.color.accent)
        swipe.setOnRefreshListener { reload(); swipe.isRefreshing = false }
        showLoading()
        reload()
        return v
    }

    protected fun onMain(run: () -> Unit) {
        if (isAdded && activity != null) activity!!.runOnUiThread(run)
    }

    protected fun showLoading() {
        content.removeAllViews()
        val tv = TextView(requireContext())
        tv.text = "加载中…"
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        tv.gravity = Gravity.CENTER
        content.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200))
    }

    protected fun showError() {
        content.removeAllViews()
        val wrap = LinearLayout(requireContext())
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER
        wrap.setPadding(40, 60, 40, 20)
        val tv = TextView(requireContext())
        tv.text = "数据加载失败\n请检查网络后重试"
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        tv.textSize = 14f; tv.gravity = Gravity.CENTER
        wrap.addView(tv)
        val btn = Button(requireContext())
        btn.text = "重试"
        btn.setOnClickListener { showLoading(); reload() }
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        blp.topMargin = 16
        wrap.addView(btn, blp)
        content.addView(wrap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
    }

    protected fun addTitle(text: String) {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        tv.textSize = 16f; tv.setPadding(0, 14, 0, 4)
        content.addView(tv)
    }

    protected fun addText(text: String, colorRes: Int = R.color.text_secondary) {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        tv.textSize = 13f; tv.setPadding(0, 2, 0, 2)
        content.addView(tv)
    }

    protected abstract fun reload()
}
