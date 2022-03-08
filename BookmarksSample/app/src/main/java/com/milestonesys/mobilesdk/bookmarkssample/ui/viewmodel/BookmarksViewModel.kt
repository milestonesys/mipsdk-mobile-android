package com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel

import android.app.Application
import android.util.Range
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.*
import com.milestonesys.mobilesdk.bookmarkssample.data.repository.BookmarksRepository
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.Resource
import com.milestonesys.mobilesdk.bookmarkssample.utils.UrlHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

class BookmarksViewModel(
    application: Application
) : AndroidViewModel(application) {

    val loginStatus: LiveData<Resource<String>> get() = _loginStatus
    val operationStatusMessage: LiveData<String?> get() = _statusMessage
    val bookmarks: LiveData<Resource<List<BookmarkItem>>> get() = _bookmarks
    val selectedBookmark: LiveData<Resource<BookmarkItem>> get() = _selectedBookmark
    val createBookmarkData: LiveData<Resource<BookmarkCreationData>> get() = _createBookmarkData
    val cameras: LiveData<Resource<List<CameraItem>>> get() = _cameras
    val currentFrame: LiveData<FrameItem?> get() = _currentFrame
    val videoEnded: LiveData<Boolean> get() = _videoEnded
    val videoError: LiveData<Boolean> get() = _videoError

    var bookmarksEnabled = false
        private set

    var user: String? = null
        private set

    var allBookmarksLoaded = false
        private set

    var streamIsStarted = false
        private set

    var filter: FilterModel = FilterModel()
        set(value) {
            field = value
            refreshBookmarks(true)
        }

    private val _bookmarks: MutableLiveData<Resource<List<BookmarkItem>>> =
        MutableLiveData(Resource.unset(null))

    private val _cameras: MutableLiveData<Resource<List<CameraItem>>> =
        MutableLiveData(Resource.unset(null))

    private val _loginStatus: MutableLiveData<Resource<String>> =
        MutableLiveData(Resource.unset(null))

    private val _statusMessage: MutableLiveData<String?> =
        MutableLiveData(null)

    private val _selectedBookmarkId: MutableLiveData<String?> =
        MutableLiveData(null)

    private val _selectedBookmark: MutableLiveData<Resource<BookmarkItem>> =
        MutableLiveData(Resource.unset(null))

    private val _createBookmarkData: MutableLiveData<Resource<BookmarkCreationData>> =
        MutableLiveData(Resource.unset(null))

    private val _currentFrame: MutableLiveData<FrameItem?> =
        MutableLiveData(null)

    private val _videoEnded: MutableLiveData<Boolean> =
        MutableLiveData(null)

    private val _videoError: MutableLiveData<Boolean> =
        MutableLiveData(null)

    private var bookmarksRepository: BookmarksRepository? = null
    private var requestedStreamWidth = 0
    private var requestedStreamHeight = 0
    private var autoPlay = false

    fun setup(address: String, port: Int, username: String, password: String) {
        val url = UrlHelper.urlFor(address, port)
        if (url == null) {
            val errorMessage = getApplication<Application>().getString(R.string.error_connect)
            _loginStatus.value = Resource.error(errorMessage, null)
            return
        }
        _loginStatus.value = Resource.loading(null)
        var result: Resource<String> = Resource.unset(null)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository = BookmarksRepository()
                    .also {
                        result = it.setupService(
                            getApplication(),
                            url,
                            username,
                            password
                        )
                    }
            }

            if (loginStatus.value?.status == DataStatus.LOADING) {

                _loginStatus.value = result

                if (result.status == DataStatus.SUCCESS) {
                    bookmarksEnabled = bookmarksRepository?.isBookmarkEnabled() ?: false
                    user = result.data
                    loadBookmarks()
                    loadCameras()
                }
            }
        }
    }

    fun logOut() {
        _loginStatus.value = Resource.unset(null)
        _bookmarks.value = Resource.unset(null)
        _cameras.value = Resource.unset(null)

        bookmarksRepository?.releaseService()
        bookmarksRepository = null

        filter = FilterModel()
    }

    override fun onCleared() {
        super.onCleared()

        bookmarksRepository?.releaseService()
        bookmarksRepository = null
    }

    fun refreshSelectedBookmark() {
        val selectedBookmarkId = _selectedBookmarkId.value ?: return
        _selectedBookmark.value = Resource.loading(null)

        viewModelScope.launch {
            var result: Resource<BookmarkItem>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.getBookmark(selectedBookmarkId)
            }
            _selectedBookmark.postValue(result)
        }
    }

    fun selectBookmarkAt(position: Int) {
        _selectedBookmarkId.value = bookmarks.value?.data?.get(position)?.id?.toString()

        refreshSelectedBookmark()
    }

    fun clearCreateBookmarkData() {
        _createBookmarkData.value = null
    }

    fun requestBookmarkReference(cameraId: String) {
        _createBookmarkData.value = Resource.loading(null)
        viewModelScope.launch {
            var result: Resource<BookmarkCreationData>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.requestBookmarkReference(cameraId)
            }
            _createBookmarkData.postValue(result)
        }
    }

    fun createBookmark(
        cameraId: String, headline: String, time: Long,
        description: String? = "", startTime: Long? = null, endTime: Long? = null,
        reference: String? = null, callback: OperationCallback? = null
    ) {
        viewModelScope.launch {
            var result: Resource<String>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.createBookmark(
                    cameraId, headline, time, startTime, endTime, description, reference
                )
            }
            if (result?.status == DataStatus.SUCCESS) {
                callback?.onOperationEnded(true)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.success_bookmark_add)
                )
                refreshBookmarks(false)
            } else {
                callback?.onOperationEnded(false)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.error_bookmark_add)
                )
            }
        }
    }

    fun updateBookmark(
        bookmarkId: String, headline: String?, description: String?,
        time: Long?, startTime: Long?, endTime: Long?, callback: OperationCallback?
    ) {
        viewModelScope.launch {
            var result: Resource<Boolean>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.updateBookmark(
                    bookmarkId, headline, description, time, startTime, endTime
                )
            }
            if (result?.status == DataStatus.SUCCESS) {
                callback?.onOperationEnded(true)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.success_bookmark_update)
                )
                if (_selectedBookmarkId.value == bookmarkId) {
                    refreshSelectedBookmark()
                }
                refreshBookmarks(false)
            } else {
                callback?.onOperationEnded(false)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.error_bookmark_update)
                )
            }
        }
    }

    fun deleteBookmark(bookmarkId: String, callback: OperationCallback) {
        viewModelScope.launch {
            var result: Resource<Boolean>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.deleteBookmark(
                    bookmarkId
                )
            }
            if (result?.status == DataStatus.SUCCESS) {
                callback.onOperationEnded(true)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.success_bookmark_delete)
                )
                if (_selectedBookmarkId.value == bookmarkId) {
                    refreshSelectedBookmark()
                }
                refreshBookmarks(false)
            } else {
                callback.onOperationEnded(false)
                _statusMessage.postValue(
                    getApplication<Application>().getString(R.string.error_bookmark_delete)
                )
            }
        }
    }

    fun isCreateEnabledForCamera(cameraId: UUID?): Boolean {
        if (cameraId == null) return false

        return cameras.value?.data?.find {
            it.id == cameraId
        }?.createBookmarksEnabled ?: false
    }

    fun onStatusMessageDisplayed() {
        _statusMessage.value = null
    }

    fun refreshBookmarks(showLoading: Boolean = false) {
        allBookmarksLoaded = false
        loadBookmarks(showLoading)
    }

    fun loadBookmarksAfterPosition(position: Int) {
        loadBookmarks(false, position)
    }

    fun resizeVideoStream(width: Int, height: Int) {
        if (requestedStreamWidth == width && requestedStreamHeight == height) return

        requestedStreamWidth = width
        requestedStreamHeight = height

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository?.resizeVideoStream(
                    Size(width, height)
                )
            }
        }
    }

    fun startVideoStream(width: Int, height: Int) {
        val currentBookmark = selectedBookmark.value?.data ?: return

        requestedStreamWidth = width
        requestedStreamHeight = height
        streamIsStarted = true

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository?.startVideoStream(
                    currentBookmark.cameraId.toString(),
                    Size(width, height),
                    Range(currentBookmark.startTime, currentBookmark.endTime),
                    object : FrameListener {
                        override fun onFrameReceived(frame: FrameItem) {
                            if (!streamIsStarted) return
                            if (autoPlay) {
                                playCurrentStream()
                                autoPlay = false
                            }
                            _videoEnded.postValue(false)
                            _videoError.postValue(false)
                            _currentFrame.postValue(frame)
                        }

                        override fun onFramesEnded() {
                            _videoEnded.postValue(true)
                        }

                        override fun onNoFramesAvailable() {
                            _videoError.postValue(true)
                        }
                    }
                )
            }
        }
    }

    fun restartVideoStream(autoPlay: Boolean) {
        this.autoPlay = autoPlay
        stopVideoStream()
        startVideoStream(requestedStreamWidth, requestedStreamHeight)
    }

    fun stopVideoStream() {
        streamIsStarted = false
        _currentFrame.value = null
        _videoEnded.value = false
        _videoError.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository?.stopVideoStream()
            }
        }
    }

    fun playCurrentStream() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository?.play()
            }
        }
    }

    fun pauseCurrentStream() {
        autoPlay = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarksRepository?.pause()
            }
        }
    }

    private fun loadBookmarks(showLoading: Boolean = true, afterPosition: Int? = null) {

        if (loginStatus.value?.status != DataStatus.SUCCESS) return

        if (showLoading) {
            _bookmarks.value = Resource.loading(null)
        }

        val loadAfterBookmark =
            if (afterPosition != null && afterPosition >= 0)
                _bookmarks.value?.data?.get(afterPosition)
            else null

        val allValues = ArrayList(_bookmarks.value?.data ?: ArrayList())

        viewModelScope.launch {
            var result: Resource<List<BookmarkItem>>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.getBookmarks(filter, loadAfterBookmark)
            }
            if (result?.data?.size == 0) {
                allBookmarksLoaded = true
            }
            if (loadAfterBookmark != null && result?.status == DataStatus.SUCCESS) {
                allValues.addAll(result?.data ?: ArrayList())
                _bookmarks.postValue(Resource.success(allValues))
            } else {
                _bookmarks.postValue(result)
            }
        }
    }

    private fun loadCameras() {
        _cameras.value = Resource.loading(null)

        viewModelScope.launch {
            var result: Resource<List<CameraItem>>? = null
            withContext(Dispatchers.IO) {
                result = bookmarksRepository?.getCameras()
            }
            _cameras.postValue(result)
        }
    }

    interface OperationCallback {
        fun onOperationEnded(result: Boolean)
    }
}