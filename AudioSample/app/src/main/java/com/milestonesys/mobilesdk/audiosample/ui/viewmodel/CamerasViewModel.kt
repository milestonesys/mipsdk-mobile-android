package com.milestonesys.mobilesdk.audiosample.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.data.repository.CamerasRepository
import com.milestonesys.mobilesdk.audiosample.utils.Resource
import com.milestonesys.mobilesdk.audiosample.utils.UrlHelper
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.milestonesys.mobilesdk.audiosample.R
import com.milestonesys.mobilesdk.audiosample.utils.DataStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL


class CamerasViewModel(
    application: Application
) : AndroidViewModel(application) {

    private var camerasRepository: CamerasRepository? = null

    private val _cameras: MutableLiveData<Resource<List<CameraItem>>> =
        MutableLiveData(Resource.unset(null))

    private val _audioUrl: MutableLiveData<Resource<URL>> =
        MutableLiveData(Resource.unset(null))

    private val _loginStatus: MutableLiveData<Resource<Boolean>> =
        MutableLiveData(Resource.unset(null))

    private val _selectedCamera: MutableLiveData<CameraItem> =
        MutableLiveData(null)

    val loginStatus: LiveData<Resource<Boolean>> get() = _loginStatus
    val cameras: LiveData<Resource<List<CameraItem>>> get() = _cameras
    val audioUrl: LiveData<Resource<URL>> get() = _audioUrl
    val selectedCamera: LiveData<CameraItem> get() = _selectedCamera

    var audioEnabled = false

    fun setup(address: String, port: Int, username: String, password: String) {
        val url = UrlHelper.urlFor(address, port)
        if (url == null) {
            val errorMessage = getApplication<Application>().getString(R.string.error_connect)
            _loginStatus.postValue(Resource.error(errorMessage, null))
            return
        }
        _loginStatus.postValue(Resource.loading(null))
        var result: Resource<Boolean>

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                camerasRepository = CamerasRepository()
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
                _loginStatus.postValue(result)
                if (result.status == DataStatus.SUCCESS) {
                    audioEnabled = camerasRepository?.isAudioEnabled() ?: false
                    loadCameras()
                }
            }
        }
    }

    fun logOut() {
        _loginStatus.postValue(Resource.unset(null))
        _cameras.postValue(Resource.unset(null))
    }

    fun selectCameraAt(position: Int) {
        _selectedCamera.value = cameras.value?.data?.get(position)
    }

    fun playAudio() {
        _audioUrl.postValue(Resource.loading(null))

        viewModelScope.launch {
            var result: Resource<URL>? = null
            withContext(Dispatchers.IO) {
                selectedCamera.value?.let {
                    result = camerasRepository?.playAudio(it.micId)
                }
            }
            if (audioUrl.value?.status == DataStatus.LOADING) {
                _audioUrl.postValue(result)
            }
        }
    }

    fun stopAudio() {
        _audioUrl.postValue(Resource.unset(null))

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                camerasRepository?.stopAudio()
            }
        }
    }

    private fun loadCameras() {
        _cameras.postValue(Resource.loading(null))

        viewModelScope.launch {
            var result: Resource<List<CameraItem>>?
            withContext(Dispatchers.IO) {
                result = camerasRepository?.getCameras()
            }
            _cameras.postValue(result)
        }
    }
}