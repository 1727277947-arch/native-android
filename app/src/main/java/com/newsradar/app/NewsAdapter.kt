package com.newsradar.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.newsradar.app.data.NewsItem

class NewsAdapter(
    private val items: List<NewsItem>,
    private val onToggleStar: (NewsItem) -> Unit,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val source: TextView = view.findViewById(R.id.item_source)
        val score: TextView = view.findViewById(R.id.item_score)
        val title: TextView = view.findViewById(R.id.item_title)
        val summary: TextView = view.findViewById(R.id.item_summary)
        val priority: TextView = view.findViewById(R.id.item_priority)
        val topic: TextView = view.findViewById(R.id.item_topic)
        val time: TextView = view.findViewById(R.id.item_time)
        val star: TextView = view.findViewById(R.id.item_star)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.source.text = item.source
        h.title.text = item.title
        h.summary.text = item.summary
        h.priority.text = item.priority
        h.topic.text = item.topic
        h.time.text = shortenTime(item.pubTime)
        h.score.text = item.score.toString()
        h.score.setBackgroundResource(
            when {
                item.score >= 80 -> R.drawable.bg_score_high
                item.score >= 65 -> R.drawable.bg_score_mid
                else -> R.drawable.bg_score_low
            }
        )
        h.star.text = if (item.starred) "★" else "☆"
        h.itemView.setOnClickListener { onClick(item) }
        h.star.setOnClickListener { onToggleStar(item) }
    }

    private fun shortenTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val t = java.time.OffsetDateTime.parse(iso)
            val n = java.time.OffsetDateTime.now()
            val mins = java.time.Duration.between(t, n).toMinutes()
            when {
                mins < 60 -> "$mins 分钟前"
                mins < 1440 -> "${mins / 60} 小时前"
                else -> "${mins / 1440} 天前"
            }
        } catch (e: Exception) { iso }
    }
}
