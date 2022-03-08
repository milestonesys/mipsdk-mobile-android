package com.example.milestone.playbacksample

import android.app.Application
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.CommunicationItem

/**
 * Application class that is used to store MIPSDKMobile instance
 */

const val PARAM_CAMERA_NAME = "cameraName"
const val PARAM_CAMERA_ID = "cameraId"

class SDKSampleApplication : Application() {

    //SDK instance stored to be used in the entire application
    var mipSdkMobile: MIPSDKMobile? = null

    // Array list with all the available cameras
    var allAvailableCameras = arrayListOf<CommunicationItem>()
}