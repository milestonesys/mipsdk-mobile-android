package com.milestonesys.mobilesdk.bookmarkssample.data.model

import java.util.*

data class CameraItem(
    val id: UUID,
    val name: String,
    val createBookmarksEnabled: Boolean = false,
    val editBookmarksEnabled: Boolean = false,
    val deleteBookmarksEnabled: Boolean = false
) {
    override fun toString(): String {
        return name
    }
}