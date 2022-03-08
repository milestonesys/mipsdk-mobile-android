package com.milestonesys.mobilesdk.audiosample.data.repository

import android.content.Context
import com.milestonesys.mobilesdk.audiosample.data.api.ApiService
import com.milestonesys.mobilesdk.audiosample.data.api.MipSdkMobileService
import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.utils.DataStatus
import com.milestonesys.mobilesdk.audiosample.utils.Resource
import java.net.URL
import java.util.*

class CamerasRepository {
    private var mipSdkMobileService: ApiService? = null

    fun setupService(context: Context, url: URL, username: String, password: String): Resource<Boolean> {
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
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    fun getCameras(): Resource<List<CameraItem>>? {
        return mipSdkMobileService?.getCamerasWithAudio()
    }

    fun playAudio(micId: UUID): Resource<URL>? {
        return mipSdkMobileService?.playAudio(micId)
    }

    fun stopAudio() {
        mipSdkMobileService?.stopAudio()
    }

    fun isAudioEnabled(): Boolean {
        return mipSdkMobileService?.isAudioEnabled() ?: false
    }
}