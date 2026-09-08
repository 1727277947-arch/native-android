package com.newsradar.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.newsradar.app.data.NewsItem

class StarredAdapter(
    private val items: List<NewsItem>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<StarredAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val source: TextView = view.findViewById(R.id.item_source)
        val title: TextView = view.findViewById(R.id.item_title)
        val time: TextView = view.findViewById(R.id.item_time)
        val topic: TextView = view.findViewById(R.id.item_topic)
        val priority: TextView = view.findViewById(R.id.item_priority)
        val summary: TextView = view.findViewById(R.id.item_summary)
        val star: TextView = view.findViewById(R.id.item_star)
        val score: TextView = view.findViewById(R.id.item_score)
    }
    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_news, p, false))
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.source.text = item.source
        h.title.text = item.title
        h.summary.text = item.summary
        h.priority.text = item.priority
        h.topic.text = item.topic
        h.time.text = ""
        h.score.text = item.score.toString()
        h.star.text = "★"
        h.itemView.setOnClickListener { onClick(item) }
    }
}
