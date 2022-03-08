package com.milestonesys.mobilesdk.bookmarkssample.data.model

interface FrameListener {
    fun onFrameReceived(frame: FrameItem)
    fun onNoFramesAvailable()
    fun onFramesEnded()
}