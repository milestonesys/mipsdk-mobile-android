package com.milestonesys.mobilesdk.audiosample.data.api

import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.utils.Resource
import java.net.URL
import java.util.*

interface ApiService {
    fun getCamerasWithAudio(): Resource<List<CameraItem>>
    fun playAudio(micId: UUID) : Resource<URL>
    fun stopAudio()
    fun isAudioEnabled(): Boolean
}