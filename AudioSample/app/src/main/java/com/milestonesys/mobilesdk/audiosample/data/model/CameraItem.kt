package com.milestonesys.mobilesdk.audiosample.data.model

import java.util.*

data class CameraItem (
    val cameraId: UUID,
    val cameraName: String,
    val micId: UUID,
    val micName: String,
    val micLiveAudio: Boolean
)