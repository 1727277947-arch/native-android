package com.newsradar.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 本地刷新日志：记录每次手动/自动刷新的时间、结果与数据量，便于在“我的”页查看 */
object RefreshLog {
    private const val PREFS = "nr_refresh"
    private const val KEY = "journal"
    private const val MAX = 40

    private fun safe(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(ctx: Context, kind: String, ok: Boolean, live: Boolean, newsCount: Int, priceCount: Int, updatedAt: String) {
        val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val o = JSONObject()
        o.put("time", time)
        o.put("kind", kind)
        o.put("ok", ok)
        o.put("live", live)
        o.put("news", newsCount)
        o.put("prices", priceCount)
        o.put("updated", updatedAt)
        try {
            val arr = JSONArray(safe(ctx).getString(KEY, "[]"))
            arr.put(o)
            // 只保留最近 MAX 条
            val keep = JSONArray()
            val start = if (arr.length() > MAX) arr.length() - MAX else 0
            for (i in start until arr.length()) keep.put(arr.get(i))
            safe(ctx).edit().putString(KEY, keep.toString()).apply()
        } catch (e: Exception) {
            val arr = JSONArray()
            arr.put(o)
            safe(ctx).edit().putString(KEY, arr.toString()).apply()
        }
    }

    fun list(ctx: Context): JSONArray {
        return try {
            JSONArray(safe(ctx).getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun clear(ctx: Context) {
        safe(ctx).edit().remove(KEY).apply()
    }
}
