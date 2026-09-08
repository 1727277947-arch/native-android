package com.newsradar.app

import android.content.Context
import com.newsradar.app.data.NewsItem
import org.json.JSONArray

object NewsStore {
    private const val PREFS = "nr_store"
    private const val STAR = "starred"

    // 内存缓存：加载的新闻列表，供收藏页复用
    @Volatile var current: List<NewsItem> = emptyList()

    private fun getArray(c: Context): JSONArray {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STAR, "[]")
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    fun isStarred(c: Context, key: String): Boolean {
        val a = getArray(c)
        for (i in 0 until a.length()) if (a.optString(i) == key) return true
        return false
    }

    fun starred(c: Context): List<NewsItem> {
        val a = getArray(c)
        val keys = HashSet<String>()
        for (i in 0 until a.length()) keys.add(a.optString(i))
        return current.filter { keys.contains(it.dedupKey) }
    }

    fun toggle(c: Context, key: String): Boolean {
        val a = getArray(c)
        val out = JSONArray()
        var removed = false
        for (i in 0 until a.length()) {
            val k = a.optString(i)
            if (k == key) removed = true else out.put(k)
        }
        if (!removed) out.put(key)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STAR, out.toString()).apply()
        return !removed
    }
}
