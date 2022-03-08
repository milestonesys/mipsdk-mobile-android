package com.milestonesys.mobilesdk.bookmarkssample.data.model

data class BookmarkCreationData(
    val cameraId: String,
    val bookmarkTime: Long,
    val preBookmarkTime: Long,
    val postBookmarkTime: Long,
    val reference: String
)