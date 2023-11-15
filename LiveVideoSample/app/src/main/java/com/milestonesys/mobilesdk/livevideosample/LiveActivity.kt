package com.milestonesys.mobilesdk.livevideosample

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.milestonesys.mipsdkmobile.callbacks.VideoReceiver
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.LiveVideo
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import java.io.ByteArrayInputStream
import java.io.IOException

private const val LIVE_EVENT_CONNECTION_LOST = 0x10
private const val DEFAULT_WIDTH = "640"
private const val DEFAULT_HEIGHT = "480"
class LiveActivity : AppCompatActivity(), VideoReceiver {

    private var applicationObject: SDKSampleApplication? = null
    private var liveVideo: LiveVideo? = null

    private var cameraName: String? = null
    private var cameraId: String? = null
    private var videoView: ImageView? = null
    private var statusView: TextView? = null

    private val logTag = LiveActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        //Image view to show video on
        videoView = findViewById(R.id.videoView)
        statusView = findViewById(R.id.status_txt)

        applicationObject = application as SDKSampleApplication?

        intent?.let {
            cameraName = it.getStringExtra(PARAM_CAMERA_NAME)
            cameraId = it.getStringExtra(PARAM_CAMERA_ID)
        }

        cameraId?.let { requestVideo(it) }
    }

    /**
     * Request video from the selected camera
     *
     * @param cameraId The id of the camera
     */
    private fun requestVideo(cameraId: String) {
        val sdkConnection = applicationObject?.mipSdkMobile ?: return

        val videoProps = HashMap<String, String>().apply {
            set(CommunicationCommand.PARAM_WIDTH, DEFAULT_WIDTH)
            set(CommunicationCommand.PARAM_HEIGHT, DEFAULT_HEIGHT)
        }

        val requestParams = HashMap<String, Any>().apply {
            set(LiveVideo.CAMERA_ID_PROPERTY, cameraId)
            set(LiveVideo.VIDEO_PROPERTIES, videoProps)
        }

        liveVideo = LiveVideo(sdkConnection, this, requestParams).apply {
            // Optionally update the used video method from push (default) to pull:
            // isPull = true
        }
        val errorResult = liveVideo?.requestVideo()
        if (errorResult != null) {
            showStatus(getString(R.string.video_error))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        liveVideo?.StopVideo()
    }

    /**
     * Status message overlay. Can be called from any thread.
     *
     * @param msg Message text to display
     */
    private fun showStatus(msg: String) {
        runOnUiThread {
            statusView?.text = msg
            videoView?.setImageResource(android.R.color.transparent)
        }
    }

    /**
     * Called on each received video frame
     *
     * @param videoCommand the server response containing the frame (headers + image)
     */
    override fun receiveVideo(videoCommand: VideoCommand) {
        // Check for connection lost
        val currentFlags = videoCommand.headerLiveEvents?.currentFlags ?: 0
        if (currentFlags and LIVE_EVENT_CONNECTION_LOST != 0) {
            showStatus(getString(R.string.msg_connection_lost))
            return
        }

        // The image data are contained in the payload
        if (videoCommand.payloadSize > 0) {
            val inputStream =
                ByteArrayInputStream(videoCommand.payload, 0, videoCommand.payloadSize)
            val bmp = BitmapFactory.decodeStream(inputStream)
            try {
                if (bmp != null) {
                    runOnUiThread { videoView?.setImageBitmap(bmp) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(logTag, "Error decoding image")
            } finally {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}