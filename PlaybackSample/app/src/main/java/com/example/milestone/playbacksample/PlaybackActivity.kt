package com.example.milestone.playbacksample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.milestonesys.mipsdkmobile.RecordData
import com.milestonesys.mipsdkmobile.callbacks.VideoReceiver
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.LiveVideo
import com.milestonesys.mipsdkmobile.communication.PlaybackVideo
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import java.io.ByteArrayInputStream
import java.text.DateFormat
import java.util.*

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
    private var sequences: List<RecordData>? = null
    private var timeStamp: Long? = null
    private var timeStampView: TextView? = null
    private val paramUserDownSampling = "No"
    private val paramResizeAvailable = "Yes"
    private val paramFps = "8"
    private val sequenceDaysLength = 7
    private val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)

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

        Thread {
            playbackVideo =
                applicationObject?.mipSdkMobile?.requestPlaybackVideo(this, allProperties)

            sequences = playbackVideo?.GetSequences(
                Calendar.getInstance().timeInMillis,
                sequenceDaysLength * 24 * 60 * 60L,
                0L
            )
            if (sequences.isNullOrEmpty()) {
                runOnUiThread {
                    timeStampView?.text = getString(R.string.no_recordings, sequenceDaysLength)
                }
            }
        }.start()
    }

    private fun stopVideo() {
        val videoId = playbackVideo?.videoId
        if (!videoId.isNullOrEmpty()) {
            Thread {
                applicationObject?.mipSdkMobile?.stopVideoStream(
                    videoId, null, null
                )
            }.start()
        }
    }

    private fun setUpButtons() {
        //Adding play forward button click listener
        val btnForward = findViewById<ImageButton>(R.id.play_forwards)
        btnForward.setOnClickListener { //Makes play forward call in separate thread via the playback object
            Thread { playbackVideo?.PlayForward() }.start()
        }
        //Adding play backward button click listener
        val btnBackward = findViewById<ImageButton>(R.id.play_backwards)
        btnBackward.setOnClickListener { //Makes play backward call in separate thread via the playback object
            Thread { playbackVideo?.PlayBackward() }.start()
        }
        //Adding pause button click listener
        val btnPause = findViewById<ImageButton>(R.id.pause)
        btnPause.setOnClickListener { //Makes pause call in separate thread via the playback object
            Thread { playbackVideo?.Pause() }.start()
        }
        //Adding play fast forward button click listener
        val btnFastForward = findViewById<ImageButton>(R.id.fast_forwards)
        btnFastForward.setOnClickListener { //Makes fast forward call in separate thread via the playback object
            Thread { playbackVideo?.ChangeSpeed(4.0f) }.start()
        }
        //Adding play fast backward button click listener
        val btnFastBackward = findViewById<ImageButton>(R.id.fast_backwards)
        btnFastBackward.setOnClickListener { //Makes fast backward call in separate thread via the playback object
            Thread { playbackVideo?.ChangeSpeed(-4.0f) }.start()
        }
        //Adding go to first recording button click listener
        val btnFirstRecording = findViewById<Button>(R.id.first_recording)
        btnFirstRecording.setOnClickListener { //Makes go to time call in separate thread via the playback object
            sequences?.firstOrNull()?.startTimeStamp?.let {
                Thread { playbackVideo?.GoToTime(it) }.start()
            }
        }
        //Adding go to last recording button click listener
        val btnLastRecording = findViewById<Button>(R.id.last_recording)
        btnLastRecording.setOnClickListener { //Makes go to time call in separate thread via the playback object
            sequences?.lastOrNull()?.startTimeStamp?.let {
                Thread { playbackVideo?.GoToTime(it) }.start()
            }
        }

        timeStampView = findViewById(R.id.video_info)
    }

    override fun onPause() {
        super.onPause()
        Thread { playbackVideo?.Pause() }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVideo()
    }

    private fun updateTimeStamp(newTimeStamp: Long) {
        timeStamp = newTimeStamp
        timeStampView?.text = formatter.format(timeStamp)
    }

    /**
     * Converts response from ByteArrayInputStream to bitmap and presents it in imageView
     */
    override fun receiveVideo(videoCommand: VideoCommand) {
        if (videoCommand.payloadSize > 0) {
            runOnUiThread { updateTimeStamp(videoCommand.timeStamp) }
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
