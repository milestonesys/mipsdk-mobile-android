package com.milestonesys.mobilesdk.bookmarkssample.data.repository

import android.content.Context
import android.util.Range
import android.util.Size
import com.milestonesys.mobilesdk.bookmarkssample.data.api.ApiService
import com.milestonesys.mobilesdk.bookmarkssample.data.api.MipSdkMobileService
import com.milestonesys.mobilesdk.bookmarkssample.data.model.*
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.Resource
import java.net.URL

class BookmarksRepository {
    private var mipSdkMobileService: ApiService? = null

    fun setupService(
        context: Context,
        url: URL,
        username: String,
        password: String
    ): Resource<String> {
        var errorMessage: String? = null

        mipSdkMobileService =
            MipSdkMobileService(context, url).also {
                with(it.connect()) {
                    if (status == DataStatus.ERROR) {
                        errorMessage = message
                    } else {
                        with(it.logIn(username, password)) {
                            if (status == DataStatus.ERROR) {
                                errorMessage = message
                            }
                        }
                    }
                }
            }

        return if (errorMessage == null) {
            Resource.success(username)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    fun releaseService() {
        mipSdkMobileService?.disconnect()
        mipSdkMobileService = null
    }

    fun getBookmarks(
        filter: FilterModel, afterBookmark: BookmarkItem?
    ): Resource<List<BookmarkItem>>? {
        return mipSdkMobileService?.getBookmarks(
            afterBookmark?.id?.toString(),
            filter.text,
            filter.timeIntervalEnd,
            filter.timeIntervalStart,
            filter.camera?.id?.toString(),
            filter.mineOnly
        )
    }

    fun getBookmark(bookmarkId: String): Resource<BookmarkItem>? {
        return mipSdkMobileService?.getBookmark(bookmarkId)
    }

    fun updateBookmark(
        bookmarkId: String, headline: String?, description: String?,
        time: Long?, startTime: Long?, endTime: Long?
    ): Resource<Boolean>? {
        return mipSdkMobileService?.updateBookmark(
            bookmarkId, headline, description, time, startTime, endTime
        )
    }

    fun deleteBookmark(bookmarkId: String): Resource<Boolean>? {
        return mipSdkMobileService?.deleteBookmark(bookmarkId)
    }

    fun createBookmark(
        cameraId: String, headline: String, time: Long,
        startTime: Long?, endTime: Long?, description: String?, reference: String?
    ): Resource<String>? {

        return mipSdkMobileService?.createBookmark(
            cameraId, headline, time, startTime, endTime, description, reference
        )
    }

    fun requestBookmarkReference(cameraId: String): Resource<BookmarkCreationData>? {
        return mipSdkMobileService?.requestBookmarkReference(cameraId)
    }

    fun getCameras(): Resource<List<CameraItem>>? {
        return mipSdkMobileService?.getCameras()
    }

    fun play() {
        mipSdkMobileService?.play()
    }

    fun pause() {
        mipSdkMobileService?.pause()
    }

    fun startVideoStream(
        cameraId: String,
        videoSize: Size,
        timeRange: Range<Long>,
        frameListener: FrameListener?
    ) {
        mipSdkMobileService?.startVideoStream(cameraId, videoSize, timeRange, frameListener)
    }

    fun stopVideoStream() {
        mipSdkMobileService?.stopVideoStream()
    }

    fun resizeVideoStream(videoSize: Size) {
        mipSdkMobileService?.resizeVideoStream(videoSize)
    }

    fun isBookmarkEnabled(): Boolean {
        return mipSdkMobileService?.isBookmarkEnabled() ?: false
    }
}