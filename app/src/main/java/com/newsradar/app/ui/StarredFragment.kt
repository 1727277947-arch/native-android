package com.newsradar.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.newsradar.app.NewsStore
import com.newsradar.app.R
import com.newsradar.app.StarredAdapter
import com.newsradar.app.data.NewsItem

class StarredFragment : Fragment() {
    private lateinit var list: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_starred, container, false)
        list = v.findViewById(R.id.starred_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        return v
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        val ctx = requireContext()
        val starred = NewsStore.starred(ctx)
        list.adapter = StarredAdapter(starred) { it: NewsItem ->
            if (it.url.isBlank()) { Toast.makeText(ctx, "链接不可用", Toast.LENGTH_SHORT).show(); return@StarredAdapter }
            com.newsradar.app.ArticleActivity.open(ctx, it.title, it.url, it.summary)
        }
    }
}
