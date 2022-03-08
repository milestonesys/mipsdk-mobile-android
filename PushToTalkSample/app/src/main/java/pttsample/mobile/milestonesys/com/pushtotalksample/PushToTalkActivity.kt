package pttsample.mobile.milestonesys.com.pushtotalksample

import android.Manifest
import android.animation.ValueAnimator
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.CommunicationItem
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import kotlinx.android.synthetic.main.activity_push_to_talk.*
import pttsample.mobile.milestonesys.com.pushtotalksample.PushToTalkSampleApplication.Companion.PARAM_CAMERA_NAME


class PushToTalkActivity : AppCompatActivity(), PushToTalkErrorsCallback {

    companion object {
        const val NUMBER_OF_AUDIO_CHANNELS = "1"
    }

    /**
     * Application object that contains all the data that should be shared between activities
     */
    private var applicationObject: PushToTalkSampleApplication? = null

    /**
     * Push to talk manager that takes the audio frames and passes them to the MoS
     */
    private var pushToTalkEngine: PushToTalkEngine = PushToTalkEngine()

    /**
     * Speaker id that is needed for the requesting stream id from MoS.
     */
    private var speakerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_push_to_talk)

        applicationObject = application as? PushToTalkSampleApplication
        val cameraName = intent.getStringExtra(PARAM_CAMERA_NAME)

        title = cameraName

        applicationObject?.camerasWithSpeakers?.forEach { currentItem: CommunicationItem ->
            if (currentItem.name == cameraName) {
                speakerId = getSpeakerId(currentItem)
            }
        }

        setupViews()
    }

    private fun setupViews() {
        btnPTT.setOnTouchListener(fun(v: View, event: MotionEvent): Boolean {
            if (!hasMicrophonePermissions()) {
                return true
            }
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_POINTER_DOWN) {
                if (pushToTalkEngine.isRecording) {
                    return true
                }
                requestStreamAndStartPTT(speakerId)
                startAnimating(v)
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_POINTER_UP) {
                stopPushToTalk()
                v.clearAnimation()
            }

            return true
        })
    }

    /**
     * Check if we have permissions to take the voice from the microphone.
     */
    private fun hasMicrophonePermissions(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            return if (audioPermission == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                false
            }
        }
        return true
    }


    /**
     * Requests stream id from the MoS and start pushing audio frames.
     */
    private fun requestStreamAndStartPTT(speakerId: String) {
        Thread(Runnable {
            applicationObject?.mipSdkMobile?.requestAudioStreamIn(
                speakerId,
                PushToTalkEngine.AUDIO_ENCODING,
                PushToTalkEngine.RECORDER_SAMPLE_RATE.toString(),
                PushToTalkEngine.BITS_PER_SAMPLE,
                NUMBER_OF_AUDIO_CHANNELS,
                {
                    val streamId = it?.outputParam?.get(CommunicationCommand.PARAM_STREAM_ID)
                    if (streamId.isNullOrEmpty()) {
                        showErrorRequestStreamToast()
                        return@requestAudioStreamIn
                    }
                    runOnUiThread {
                        startPushToTalk(streamId)
                    }
                },
                {
                    showErrorRequestStreamToast()
                })
        }).start()
    }

    private fun showErrorRequestStreamToast() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.error_request_stream_id), Toast.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Starts pushing audio frames to the MoS by the given stream id.
     */
    private fun startPushToTalk(streamId: String) {
        if (!pushToTalkEngine.isRecording) {
            pushToTalkEngine = PushToTalkEngine(this, streamId, this)
            pushToTalkEngine.startRecording()
        }
    }

    /**
     * Stops the push to talk.
     */
    private fun stopPushToTalk() {
        if (pushToTalkEngine.isRecording) {
            pushToTalkEngine.stopRecording()
        }
    }

    /**
     * Callback that will be called when a PTT error has been received.
     */
    override fun onErrorOccurred(errorCode: Int) {}

    /**
     * Returns the speaker id from the given camera
     */
    private fun getSpeakerId(camera: CommunicationItem): String {
        camera.itemsList.forEach { currentItem: CommunicationItem ->
            if (currentItem.type == PushToTalkSampleApplication.COMMUNICATION_ITEM_TYPE_SPEAKER) {
                return currentItem.id.toString()
            }
        }
        return ""
    }

    /**
     * Starts blinking animation to the given view
     */
    private fun startAnimating(v: View) {
        val anim: Animation = AlphaAnimation(0.0F, 1.0F)
        anim.duration = 500
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = ValueAnimator.INFINITE
        v.startAnimation(anim)
    }
}
