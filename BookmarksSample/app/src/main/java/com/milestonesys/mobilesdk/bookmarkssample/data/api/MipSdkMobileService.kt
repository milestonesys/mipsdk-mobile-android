package com.milestonesys.mobilesdk.bookmarkssample.data.api

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Range
import android.util.Size
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.*
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand.*
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.*
import com.milestonesys.mobilesdk.bookmarkssample.utils.Resource
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

private const val ITEM_TYPE_FOLDER = "Folder"
private const val ITEM_TYPE_VIEW = "View"
private const val ITEM_TYPE_CAMERA = "Camera"
private const val ITEM_LOAD_COUNT = 10

private const val PLAYBACK_EVENT_DATABASE_START = 0x10
private const val PLAYBACK_EVENT_DATABASE_END = 0x20
private const val PLAYBACK_EVENT_DATABASE_ERROR = 0x40
private const val PLAYBACK_EVENT_RANGE_NO_DATA = 0x100
private const val PLAYBACK_EVENT_OUT_OF_RANGE = 0x200

private const val EMPTY_ID = "00000000-0000-0000-0000-000000000000"

class MipSdkMobileService(context: Context, connectionUrl: URL) : ApiService {
    private val mipSdkMobile: MIPSDKMobile = MIPSDKMobile(context, connectionUrl)
    private var playbackVideo: PlaybackVideo? = null
    private var bookmarksEnabled = false

    private val errorConnectMessage = context.getString(R.string.error_connect)
    private val errorLoginMessage = context.getString(R.string.error_login)
    private val errorBookmarksMessage = context.getString(R.string.error_bookmarks)
    private val errorCamerasMessage = context.getString(R.string.error_cameras)

    private val errorBookmarkAdd = context.getString(R.string.error_bookmark_add)
    private val errorBookmarkEdit = context.getString(R.string.error_bookmark_update)
    private val errorBookmarkDelete = context.getString(R.string.error_bookmark_delete)
    private val errorBookmarkRead = context.getString(R.string.error_bookmark_read)

    override fun connect(): Resource<Boolean> {
        var errorMessage: String? = null

        mipSdkMobile.connect(
            { },
            { errorMessage = errorConnectMessage }
        )

        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun logIn(username: String, password: String): Resource<Boolean> {
        var errorMessage: String? = null

        mipSdkMobile.logIn(
            username,
            password,
            { bookmarksEnabled = it.outputParam[PARAM_SUPPORTS_BOOKMARKS] == PARAM_AUTH_YES },
            { errorMessage = errorLoginMessage }
        )

        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun disconnect() {
        mipSdkMobile.closeCommunication()
    }

    override fun getBookmarks(
        afterBookmarkId: String?,
        text: String?,
        startTime: Long?,
        endTime: Long?,
        cameraId: String?,
        myBookmarksOnly: Boolean?
    ): Resource<List<BookmarkItem>> {

        val bookmarksList = ArrayList<BookmarkItem>()
        var errorMessage: String? = null

        mipSdkMobile.getBookmarks(
            afterBookmarkId,
            ITEM_LOAD_COUNT,
            startTime,
            endTime,
            myBookmarksOnly ?: false,
            text,
            cameraId,
            { fillBookmarksList(it.itemsList, bookmarksList) },
            { errorMessage = errorBookmarksMessage }
        )

        return if (errorMessage == null) {
            Resource.success(bookmarksList)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun getBookmark(bookmarkId: String): Resource<BookmarkItem> {

        var bookmark: BookmarkItem? = null
        var errorMessage: String? = null

        mipSdkMobile.getBookmarks(
            bookmarkId,
            null,
            null,
            null,
            false,
            null,
            null,
            { bookmark = parseBookmark(it.itemsList[0]) },
            { errorMessage = errorBookmarkRead }
        )

        return if (errorMessage == null) {
            Resource.success(bookmark)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun updateBookmark(
        bookmarkId: String, headline: String?, description: String?,
        time: Long?, startTime: Long?, endTime: Long?,
    ): Resource<Boolean> {
        var errorMessage: String? = null
        mipSdkMobile.updateBookmark(
            bookmarkId,
            headline,
            description,
            time,
            startTime,
            endTime,
            { },
            { errorMessage = errorBookmarkEdit }
        )
        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun deleteBookmark(bookmarkId: String): Resource<Boolean> {
        var errorMessage: String? = null
        mipSdkMobile.deleteBookmark(
            bookmarkId,
            { },
            { errorMessage = errorBookmarkDelete }
        )
        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    /**
     * Creates a new bookmark with the provided details. The parameters [cameraId], [headline] and
     * [time] are mandatory, while the rest can be omitted. If [startTime] or [endTime] are null,
     * their values will be calculated based on the current settings for pre-bookmark time and
     * post-bookmark time. If [description] is null, a default value of "Mobile bookmark" will be
     * used. If [reference] is null, a new reference will be requested automatically. To see the
     * benefits of providing a reference here, read the description of [requestBookmarkReference].
     */
    override fun createBookmark(
        cameraId: String, headline: String, time: Long,
        startTime: Long?, endTime: Long?, description: String?, reference: String?
    ): Resource<String> {
        var bookmarkId: String? = null
        var errorMessage: String? = null
        mipSdkMobile.requestCreateBookmark(
            null,
            cameraId,
            description,
            headline,
            time,
            startTime,
            endTime,
            reference,
            { bookmarkId = it.outputParam[PARAM_BOOKMARK_ID] },
            { errorMessage = errorBookmarkAdd }
        )
        return if (errorMessage == null) {
            Resource.success(bookmarkId)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    /**
     * When [requestBookmarkReference] is called, a reference for a new bookmark is requested.
     * It can then be passed as a parameter in [createBookmark] when the new bookmark is actually
     * created.
     *
     * Another option to create a bookmark is to call directly [createBookmark] without passing a
     * reference, in that case such a reference will be requested automatically.
     *
     * However, there is a default rule called "Record on Bookmark" that ensures that whenever a
     * bookmark reference is requested, video is recorded automatically, so in order to take
     * advantage of this rule, in some cases we should call both methods.
     *
     * Consider the following example: The current time is 10:12:15 and the user would like to
     * create a bookmark immediately, but also provide additional information. In that case we
     * should immediately call [requestBookmarkReference] so that the rule can ensure that video is
     * recorded now. Some time later, when the user is ready with all the input, for example in
     * 10:17:25, we can call [createBookmark] and pass the provided reference. If we had missed the
     * part with the [requestBookmarkReference] and proceeded directly with [createBookmark] without
     * passing a reference the video would still be recorded - but around the time of the
     * [createBookmark] request - 10:17:25, and not for the actual bookmarked time - 10:12:15, as
     * it was the requirement.
     *
     * This method also returns information about the current settings for pre-bookmark time
     * and post-bookmark time, which can be used to calculate values for startTime and endTime
     * to be passed as parameters in [createBookmark].
     */
    override fun requestBookmarkReference(cameraId: String): Resource<BookmarkCreationData> {
        var settings: BookmarkCreationData? = null
        val bookmarkTime = System.currentTimeMillis()
        var errorMessage: String? = null
        mipSdkMobile.requestBookmarkCreation(
            cameraId,
            { cmd ->
                settings = BookmarkCreationData(
                    cameraId,
                    bookmarkTime,
                    cmd.outputParam[PARAM_BEFORE_TIME]?.toLongOrNull() ?: 0L,
                    cmd.outputParam[PARAM_AFTER_TIME]?.toLongOrNull() ?: 0L,
                    cmd.outputParam[PARAM_REFERENCE] ?: ""
                )
            },
            { errorMessage = errorBookmarkAdd }
        )

        return if (errorMessage == null) {
            Resource.success(settings)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun getCameras(): Resource<List<CameraItem>> {
        val camerasSet = HashSet<CameraItem>()
        var errorMessage: String? = null

        mipSdkMobile.getAllViewsAndCameras(
            { findCameras(it.itemsList, camerasSet) },
            { errorMessage = errorCamerasMessage }
        )

        return if (errorMessage == null) {
            Resource.success(ArrayList(camerasSet).sortedBy { it.name })
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun startVideoStream(
        cameraId: String,
        videoSize: Size,
        timeRange: Range<Long>,
        frameListener: FrameListener?
    ) {
        //Setting up video settings
        val videoProps = HashMap<String, String>()
        videoProps[PARAM_WIDTH] = videoSize.width.toString()
        videoProps[PARAM_HEIGHT] = videoSize.height.toString()
        videoProps[PARAM_USER_DOWNSAMPLING] = PARAM_AUTH_NO
        videoProps[PARAM_RESIZE_AVAILABLE] = PARAM_AUTH_YES
        videoProps[PARAM_TIME] = timeRange.lower.toString()
        videoProps[PARAM_TIME_RANGE_BEGIN] = timeRange.lower.toString()
        videoProps[PARAM_TIME_RANGE_END] = timeRange.upper.toString()

        //Setting up video channel properties
        val allProperties = HashMap<String, Any>()
        allProperties[LiveVideo.CAMERA_ID_PROPERTY] = cameraId
        allProperties[LiveVideo.VIDEO_PROPERTIES] = videoProps
        allProperties[LiveVideo.FPS_PROPERTY] = "8"

        playbackVideo = mipSdkMobile.requestPlaybackVideo(
            { receiveVideoCommand(it, frameListener) }, allProperties
        )
    }

    override fun resizeVideoStream(videoSize: Size) {
        playbackVideo?.Rescale(videoSize.width, videoSize.height)
    }

    override fun stopVideoStream() {
        val videoId = playbackVideo?.videoId
        if (!videoId.isNullOrEmpty()) {
            mipSdkMobile.stopVideoStream(
                videoId, null, null
            )
        }
    }

    override fun play() {
        playbackVideo?.PlayForward()
    }

    override fun pause() {
        playbackVideo?.Pause()
    }

    override fun isBookmarkEnabled(): Boolean {
        return bookmarksEnabled
    }

    private fun receiveVideoCommand(videoCommand: VideoCommand, frameListener: FrameListener?) {
        if (videoCommand.payloadSize > 0) {
            val inputStream =
                ByteArrayInputStream(videoCommand.payload, 0, videoCommand.payloadSize)
            val bmp = BitmapFactory.decodeStream(inputStream)
            try {
                frameListener?.onFrameReceived(FrameItem(bmp, videoCommand.timeStamp))
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            } finally {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (isNoDataAvailable(videoCommand)) {
            frameListener?.onNoFramesAvailable()
        } else if (isStreamEnded(videoCommand)) {
            frameListener?.onFramesEnded()
        }
    }

    private fun isNoDataAvailable(vCmd: VideoCommand): Boolean {
        vCmd.headerPlaybackEvents?.currentFlags?.let {
            if (
                it and PLAYBACK_EVENT_DATABASE_ERROR == PLAYBACK_EVENT_DATABASE_ERROR ||
                it and PLAYBACK_EVENT_RANGE_NO_DATA == PLAYBACK_EVENT_RANGE_NO_DATA
            ) return true
        }
        return false
    }

    private fun isStreamEnded(vCmd: VideoCommand): Boolean {
        vCmd.headerPlaybackEvents?.currentFlags?.let {
            if (
                it and PLAYBACK_EVENT_DATABASE_START == PLAYBACK_EVENT_DATABASE_START ||
                it and PLAYBACK_EVENT_DATABASE_END == PLAYBACK_EVENT_DATABASE_END ||
                it and PLAYBACK_EVENT_OUT_OF_RANGE == PLAYBACK_EVENT_OUT_OF_RANGE
            ) return true
        }
        return false
    }

    private fun findCameras(items: List<CommunicationItem>, container: HashSet<CameraItem>) {
        for (item in items) {
            when (item.type) {
                ITEM_TYPE_FOLDER, ITEM_TYPE_VIEW ->
                    findCameras(item.itemsList, container)

                ITEM_TYPE_CAMERA -> {
                    container.add(
                        CameraItem(
                            item.id,
                            item.name,
                            item.itemProperties[PARAM_BOOKMARK_CREATE_ENABLED] == PARAM_AUTH_YES,
                            item.itemProperties[PARAM_BOOKMARK_EDIT_ENABLED] == PARAM_AUTH_YES,
                            item.itemProperties[PARAM_BOOKMARK_DELETE_ENABLED] == PARAM_AUTH_YES
                        )
                    )
                }
            }
        }
    }

    private fun fillBookmarksList(
        items: List<CommunicationItem>,
        container: ArrayList<BookmarkItem>
    ) {
        for (item in items) {
            if (item.type.equals(PARAM_BOOKMARK)) {
                container.add(parseBookmark(item))
            }
        }
    }

    private fun parseBookmark(bookmarkCommunicationItem: CommunicationItem): BookmarkItem {
        val cameraItem =
            if (bookmarkCommunicationItem.itemsList.isEmpty()) null
            else bookmarkCommunicationItem.itemsList[0]
        return BookmarkItem(
            bookmarkCommunicationItem.id,
            bookmarkCommunicationItem.name,
            bookmarkCommunicationItem.itemProperties[PARAM_DESCRIPTION] ?: "",
            bookmarkCommunicationItem.itemProperties[SEQ_START_TIME]?.toLong() ?: 0L,
            bookmarkCommunicationItem.itemProperties[PARAM_TIME]?.toLong() ?: 0L,
            bookmarkCommunicationItem.itemProperties[SEQ_END_TIME]?.toLong() ?: 0L,
            bookmarkCommunicationItem.itemProperties[PARAM_USERNAME] ?: "",
            bookmarkCommunicationItem.itemProperties[PARAM_REFERENCE] ?: "",
            cameraItem?.id ?: UUID.fromString(EMPTY_ID),
            cameraItem?.name ?: "",
        )
    }
}