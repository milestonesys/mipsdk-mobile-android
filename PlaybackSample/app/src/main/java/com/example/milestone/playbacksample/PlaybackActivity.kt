package com.example.milestone.playbacksample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import java.util.*

import com.milestonesys.mipsdkmobile.callbacks.VideoReceiver
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import android.graphics.Bitmap
import android.widget.ImageButton
import android.widget.ImageView
import com.milestonesys.mipsdkmobile.communication.LiveVideo
import com.milestonesys.mipsdkmobile.communication.PlaybackVideo

/**
 * Activity that is playing playback video using MIPSDKMobile
 */
class PlaybackActivity : AppCompatActivity(), VideoReceiver {

    private var applicationObject: SDKSampleApplication? = null
    private var cameraName: String? = null
    private var cameraId: UUID? = null
    private var bmp: Bitmap? = null
    private var videoView: ImageView? = null
    private var playbackVideo: PlaybackVideo? = null
    private val paramUserDownSampling = "No"
    private val paramResizeAvailable = "Yes"
    private val paramFps = "8"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        //Image view to show video on
        videoView = findViewById(R.id.video_view)
        applicationObject = application as SDKSampleApplication?

        //Parsing data from the intent to request video
        val currentIntent = intent

        cameraName = currentIntent.getStringExtra(PARAM_CAMERA_NAME)
        cameraId = UUID.fromString(currentIntent.getStringExtra(PARAM_CAMERA_ID))

        setUpVideoPlayback()
        setUpButtons()
    }

    private fun setUpVideoPlayback() {
        //Setting up video settings
        val videoProps = HashMap<String, String>()
        videoProps[CommunicationCommand.PARAM_WIDTH] = videoView?.width.toString()
        videoProps[CommunicationCommand.PARAM_HEIGHT] = videoView?.height.toString()
        videoProps[CommunicationCommand.PARAM_USER_DOWNSAMPLING] = paramUserDownSampling
        videoProps[CommunicationCommand.PARAM_RESIZE_AVAILABLE] = paramResizeAvailable

        //Setting up video channel properties
        val allProperties = HashMap<String, Any>()
        allProperties[LiveVideo.CAMERA_ID_PROPERTY] = cameraId.toString()
        allProperties[LiveVideo.VIDEO_PROPERTIES] = videoProps
        allProperties[LiveVideo.FPS_PROPERTY] = paramFps

        Thread(Runnable {
            playbackVideo = applicationObject?.mipSdkMobile?.requestPlaybackVideo(this, allProperties)
        }).start()
    }

    private fun stopVideo() {
        val videoId = playbackVideo?.videoId
        if (!videoId.isNullOrEmpty()) {
            Thread(Runnable {
                applicationObject?.mipSdkMobile?.stopVideoStream(
                    videoId, null, null)
            }).start()
        }
    }

    private fun setUpButtons() {
        //Adding play forward button click listener
        val btnForward = findViewById<ImageButton>(R.id.play_forwards)
        btnForward.setOnClickListener { //Makes play forward call in separate thread via the playback object
            Thread(Runnable { playbackVideo?.PlayForward() }).start()
        }
        //Adding play backward button click listener
        val btnBackward = findViewById<ImageButton>(R.id.play_backwards)
        btnBackward.setOnClickListener { //Makes play backward call in separate thread via the playback object
            Thread(Runnable { playbackVideo?.PlayBackward() }).start()
        }
        //Adding pause button click listener
        val btnPause = findViewById<ImageButton>(R.id.pause)
        btnPause.setOnClickListener { //Makes pause call in separate thread via the playback object
            Thread(Runnable { playbackVideo?.Pause() }).start()
        }
    }

    override fun onPause() {
        super.onPause()
        Thread(Runnable { playbackVideo?.Pause() }).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVideo()
    }

    /**
     * Converts response from ByteArrayInputStream to bitmap and presents it in imageView
     */
    override fun receiveVideo(videoCommand: VideoCommand) {
        if (videoCommand.payloadSize > 0) {
            val inputStream = ByteArrayInputStream(videoCommand.payload, 0, videoCommand.payloadSize)
            bmp = BitmapFactory.decodeStream(inputStream)
            try {
                runOnUiThread {
                    if (bmp != null) {
                        videoView?.setImageBitmap(bmp)
                    }
                }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            } finally {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
