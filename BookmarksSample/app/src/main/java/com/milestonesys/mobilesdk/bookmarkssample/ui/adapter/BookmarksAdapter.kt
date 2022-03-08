package com.milestonesys.mobilesdk.bookmarkssample.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkItem
import com.milestonesys.mobilesdk.bookmarkssample.databinding.ItemBookmarkBinding
import com.milestonesys.mobilesdk.bookmarkssample.databinding.ItemLoadMoreBinding
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper
import java.util.*
import kotlin.collections.HashMap

class BookmarksAdapter(
    private val bookmarks: ArrayList<BookmarkItem>,
    private val clickListener: BookmarkClickListener,
    private val loadingListener: BookmarkLoadingListener
) : RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder>() {

    private val stableIds = HashMap<UUID, Long>()
    private var nextStableId = 0L
    private var showLoadMore = false

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        return if (viewType == 0) {
            val binding = ItemBookmarkBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)

            BookmarkViewHolder(binding, clickListener)
        } else {
            val binding = ItemLoadMoreBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)

            LoadingViewHolder(binding)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == bookmarks.size) {
            1
        } else {
            0
        }
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        if (holder is LoadingViewHolder) {
            loadingListener.onLoadingRequested(position - 1)
            return
        }
        with(holder) {
            bookmarks[position].let { item ->
                binding?.let {
                    it.textViewBookmarkName.text = item.name
                    it.textViewBookmarkDescription.text = DateHelper.formatted(item.eventTime)
                    it.textViewBookmarkCamera.text = item.cameraName
                    it.root.transitionName = item.id.toString()
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        if (position in 0 until bookmarks.size) {
            return stableIds[bookmarks[position].id] ?: 0
        }
        return 0
    }

    override fun getItemCount(): Int = bookmarks.size + (if (showLoadMore) 1 else 0)

    private fun addData(list: List<BookmarkItem>) {

        list.forEach {
            if (!stableIds.containsKey(it.id)) {
                stableIds[it.id] = nextStableId++
            }
            bookmarks.add(it)
        }
    }

    fun replaceData(list: List<BookmarkItem>, allBookmarksLoaded: Boolean) {

        if (allBookmarksLoaded && showLoadMore) {
            showLoadMore = false
            notifyItemRemoved(bookmarks.size)
        } else if (!allBookmarksLoaded && !showLoadMore) {
            showLoadMore = true
            notifyItemInserted(bookmarks.size)
        }

        bookmarks.clear()
        addData(list)
    }

    inner class LoadingViewHolder(
        binding: ItemLoadMoreBinding
    ) : BookmarkViewHolder(binding.root)

    open inner class BookmarkViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        var binding: ItemBookmarkBinding? = null

        constructor(
            binding: ItemBookmarkBinding,
            listener: BookmarkClickListener
        ) : this(binding.root) {
            binding.root.setOnClickListener {
                listener.onClick(it, bindingAdapterPosition)
            }
            this.binding = binding
        }
    }

    interface BookmarkClickListener {
        fun onClick(view: View, position: Int)
    }

    interface BookmarkLoadingListener {
        fun onLoadingRequested(position: Int)
    }
}