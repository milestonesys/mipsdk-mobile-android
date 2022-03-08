package com.milestonesys.mobilesdk.bookmarkssample.data.api

import android.util.Range
import android.util.Size
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkCreationData
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkItem
import com.milestonesys.mobilesdk.bookmarkssample.data.model.CameraItem
import com.milestonesys.mobilesdk.bookmarkssample.data.model.FrameListener
import com.milestonesys.mobilesdk.bookmarkssample.utils.Resource

interface ApiService {

    fun connect(): Resource<Boolean>

    fun logIn(username: String, password: String): Resource<Boolean>

    fun disconnect()

    fun getBookmarks(
        afterBookmarkId: String?,
        text: String?,
        startTime: Long?,
        endTime: Long?,
        cameraId: String?,
        myBookmarksOnly: Boolean?
    ): Resource<List<BookmarkItem>>

    fun getBookmark(bookmarkId: String): Resource<BookmarkItem>

    fun updateBookmark(
        bookmarkId: String, headline: String?, description: String?,
        time: Long?, startTime: Long?, endTime: Long?,
    ): Resource<Boolean>

    fun deleteBookmark(bookmarkId: String): Resource<Boolean>

    fun createBookmark(
        cameraId: String, headline: String, time: Long,
        startTime: Long?, endTime: Long?, description: String?, reference: String?
    ): Resource<String>

    fun requestBookmarkReference(cameraId: String): Resource<BookmarkCreationData>

    fun getCameras(): Resource<List<CameraItem>>

    fun startVideoStream(
        cameraId: String,
        videoSize: Size,
        timeRange: Range<Long>,
        frameListener: FrameListener?
    )

    fun resizeVideoStream(videoSize: Size)

    fun stopVideoStream()

    fun play()

    fun pause()

    fun isBookmarkEnabled(): Boolean
}