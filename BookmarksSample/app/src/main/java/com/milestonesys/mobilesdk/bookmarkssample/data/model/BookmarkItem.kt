package com.milestonesys.mobilesdk.bookmarkssample.data.model

import java.util.*

data class BookmarkItem(
    val id: UUID,
    val name: String,
    val description: String,
    val startTime: Long,
    val eventTime: Long,
    val endTime: Long,
    val username: String,
    val reference: String,
    val cameraId: UUID,
    val cameraName: String
)