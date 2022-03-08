package pttsample.mobile.milestonesys.com.pushtotalksample

import android.app.Application
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.CommunicationItem

/**
 * Application class that is used to store MIPSDKMobile instance
 */
class PushToTalkSampleApplication : Application() {

    companion object {
        const val COMMUNICATION_ITEM_TYPE_CAMERA: String = "Camera"
        const val COMMUNICATION_ITEM_TYPE_SPEAKER: String = "Speaker"
        const val PARAM_CAMERA_NAME: String = "cameraName"
        const val PARAM_CAMERA_ID: String = "cameraId"
    }

    //SDK instance stored to be used in the entire application
    var mipSdkMobile: MIPSDKMobile? = null

    // Array list with all the available cameras
    var allAvailableCameras = arrayListOf<CommunicationItem>()

    // Array list with the cameras that have associated speakers to them
    var camerasWithSpeakers = arrayListOf<CommunicationItem>()

    //Address of the server that the client is connected to
    var serverHost: String = ""

    //Port of the server that the client is connected to
    var serverPort: Int = 0

}
