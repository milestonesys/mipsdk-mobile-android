package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.view.View
import android.widget.AutoCompleteTextView
import androidx.navigation.fragment.findNavController
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkItem
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper

class BookmarkEditFragment : BookmarkExtendedCreateFragment() {

    private lateinit var currentBookmark: BookmarkItem

    override fun onSaveSelected() {
        updateBookmark()
    }

    override fun setupUI() {
        super.setupUI()

        binding.bookmarkCamera.visibility = View.GONE
    }

    override fun setupObserver() {
        super.setupObserver()

        bookmarksViewModel.selectedBookmark.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.SUCCESS -> {
                    onDataLoaded()
                }
                DataStatus.ERROR -> {
                    onLoadingComplete()
                    findNavController().popBackStack()
                }
                else -> { }
            }
        })
    }

    override fun onDataLoaded() {
        currentBookmark = bookmarksViewModel.selectedBookmark.value?.data ?: return
        super.onDataLoaded()
    }

    override fun setupEditorOptions() {
        if (!this::currentBookmark.isInitialized) return

        val eventTimeInSeconds = currentBookmark.eventTime / 1000
        val startTimeInSeconds = currentBookmark.startTime / 1000
        val endTimeInSeconds = currentBookmark.endTime / 1000

        val preBookmarkTime = (eventTimeInSeconds - startTimeInSeconds).toInt()
        val postBookmarkTime = (endTimeInSeconds - eventTimeInSeconds).toInt()
        addToTimeOptions(preBookmarkTime, postBookmarkTime)
        selectedPreBookmarkTimeIndex = timesOptions!!.indexOf(preBookmarkTime)
        selectedPostBookmarkTimeIndex = timesOptions!!.indexOf(postBookmarkTime)

        super.setupEditorOptions()
    }

    override fun loadValues() {
        if (!this::currentBookmark.isInitialized) return

        bookmarkTime.timeInMillis = currentBookmark.eventTime
        binding.bookmarkHeadline.editText?.setText(currentBookmark.name)
        binding.bookmarkDescription.editText?.setText(currentBookmark.description)
        binding.bookmarkDate.editText?.setText(DateHelper.formattedDateOnly(currentBookmark.eventTime))
        binding.bookmarkTime.editText?.setText(DateHelper.formattedTime24H(currentBookmark.eventTime))

        (binding.bookmarkPreBookmarkTime.editText as? AutoCompleteTextView)?.let {
            val item = it.adapter.getItem(selectedPreBookmarkTimeIndex)
            it.setText(item.toString(), false)
        }
        (binding.bookmarkPostBookmarkTime.editText as? AutoCompleteTextView)?.let {
            val item = it.adapter.getItem(selectedPostBookmarkTimeIndex)
            it.setText(item.toString(), false)
        }
    }

    private fun updateBookmark() {
        val id = currentBookmark.id.toString()
        val headline = binding.bookmarkHeadline.editText!!.text.toString()
        val description = binding.bookmarkDescription.editText!!.text.toString()

        onLoadingStarted()

        bookmarksViewModel.updateBookmark(
            id,
            headline,
            description,
            getSelectedEventTime(),
            getSelectedStartTime(),
            getSelectedEndTime(),
            object : BookmarksViewModel.OperationCallback {
                override fun onOperationEnded(result: Boolean) {
                    if (view == null) return

                    onLoadingComplete()
                    if (result) {
                        findNavController().popBackStack()
                    }
                }
            }
        )
    }
}