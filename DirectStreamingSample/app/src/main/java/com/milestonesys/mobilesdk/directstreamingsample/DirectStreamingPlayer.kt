package com.milestonesys.mobilesdk.directstreamingsample

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.annotation.NonNull
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player.EventListener
import com.google.android.exoplayer2.Player.STATE_READY
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.milestonesys.mipsdkmobile.callbacks.VideoReceiver
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import com.milestonesys.mobilesdk.directstreamingsample.DirectStreamingPlayer.CustomPlayerHandler.Companion.MSG_FALLBACK_TIMER
import com.milestonesys.mobilesdk.directstreamingsample.DirectStreamingPlayer.CustomPlayerHandler.Companion.MSG_FORCE_PLAYER
import com.milestonesys.mobilesdk.directstreamingsample.DirectStreamingPlayer.CustomPlayerHandler.Companion.MSG_PLAYER_RESUME
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock

class DirectStreamingPlayer(private val playerView: PlayerView, ctx: Context, fallbackListener: IPlayerEventListener?) : EventListener, VideoReceiver {

    companion object {
        private const val mediaSourceBufferSize = 2
        private const val fallBackTaskDelay: Long = 4000
        private const val TAG_DS = "DSPlayer"

        interface IPlayerEventListener {
            fun fallBack()
            fun connectionLost()
        }
    }

    private val logTag = "DSPlayer"
    private val context = WeakReference(ctx)
    private val eventListenerWeakReference = WeakReference<IPlayerEventListener>(fallbackListener)

    private var released = false

    @Volatile
    private var isScheduledTask = false
    private var player: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context.get(), DefaultTrackSelector())
    private var concatenatingMediaSource: ConcatenatingMediaSource = ConcatenatingMediaSource()
    private val mediaSourceEventsHandler: Handler = Handler(CustomPlayerHandler(this, player, concatenatingMediaSource))
    private var lastAddedFragmentId = 0
    private var lastPlayedFragmentId = 0
    private val addNewSourceLocker = ReentrantLock()

    init {
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        player.addListener(this)
        playerView.player = player

        playerView.setShutterBackgroundColor(Color.BLACK)

        player.prepare(concatenatingMediaSource, false, false)
        player.playWhenReady = true

        Log.d(logTag, "Creating new video player instance: $player")
        scheduleTimer(fallBackTaskDelay)
    }

    fun getPlayerView(): PlayerView {
        return playerView
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == STATE_READY) {
            if (isScheduledTask) {
                unScheduleTimer()
            }
        } else {
            if (!isScheduledTask) {
                scheduleTimer(fallBackTaskDelay)
            }
        }
    }

    //Called when segment is fully loaded or player.seek is called
    override fun onPositionDiscontinuity(reason: Int) {
        if (lastPlayedFragmentId > mediaSourceBufferSize) {
            Thread {
                try {
                    addNewSourceLocker.lock()
                    concatenatingMediaSource.removeMediaSource(0)
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG_DS, "Еxception while trying to remove media source")
                } finally {
                    addNewSourceLocker.unlock()
                }

            }.start()
        }

        if (addNewSourceLocker.tryLock()) {
            //Pause player when the last played fragment is the last added until new fragment is passed from the MoS
            if (lastPlayedFragmentId == lastAddedFragmentId && lastPlayedFragmentId != 0) {
                player.playWhenReady = false
                if (!isScheduledTask) {
                    scheduleTimer(fallBackTaskDelay)
                }
            } else {
                unScheduleTimer()
            }
            synchronized(addNewSourceLocker) {
                addNewSourceLocker.unlock()
            }
        }
        lastPlayedFragmentId++
    }

    override fun onPlayerError(error: ExoPlaybackException?) {
        restartPlayer()
    }

    private fun restartPlayer() {
        player.release()
        player.prepare(concatenatingMediaSource, false, false)
    }

    fun release() {
        released = true
        unScheduleTimer()
        mediaSourceEventsHandler.sendEmptyMessage(CustomPlayerHandler.MSG_RELEASE_PLAYER)
    }

    /**
     * Fallback on unsupported payload type.
     * Supported payload types - H264 and H265.
     */
    private fun fallbackOnUnsupportedPayload(vCmd: VideoCommand): Boolean {
        if (vCmd.headerStreamInfo != null && vCmd.payloadType != CommunicationCommand.PARAM_PAYLOAD_TYPE_DIRECT_STREAMING_H265 &&
                vCmd.payloadType != CommunicationCommand.PARAM_PAYLOAD_TYPE_DIRECT_STREAMING_H264) {
            if (!isPaused()) {
                //Update 'playerPaused' variable in CustomPlayerHandler in order to fallback
                mediaSourceEventsHandler.sendEmptyMessage(MSG_PLAYER_RESUME)
            }
            Log.d(logTag, "Falling back to transcoding due to unsupported payload type")
            mediaSourceEventsHandler.sendEmptyMessage(MSG_FALLBACK_TIMER)
            return true
        }
        return false
    }

    override fun receiveVideo(vCmd: VideoCommand?) {

        // Check for connection lost
        if (vCmd?.headerLiveEvents != null && vCmd.headerLiveEvents.currentFlags and MainActivity.LIVE_EVENT_CONNECTION_LOST != 0) {
            this.eventListenerWeakReference.get()?.connectionLost()
            return
        }

        if (vCmd != null && fallbackOnUnsupportedPayload(vCmd)) {
            return
        }

        if (!isScheduledTask) {
            scheduleTimer(fallBackTaskDelay)
        }

        addNewFrame(vCmd)
    }

    private fun addNewFrame(vCmd: VideoCommand?) {
        vCmd?.let { videoCommand ->
            videoCommand.payloadType?.let { paylaodType ->
                if (paylaodType == CommunicationCommand.PARAM_PAYLOAD_TYPE_DIRECT_STREAMING_H264 || paylaodType == CommunicationCommand.PARAM_PAYLOAD_TYPE_DIRECT_STREAMING_H265) {
                    try {
                        addVideoToPlaylist(videoCommand.payload)
                    } catch (e: java.lang.Exception) {
                        e.message?.let { Log.d(TAG_DS, it) }
                    }
                }
            }
        }
    }

    //MANAGE MEDIA SOURCE
    private fun addVideoToPlaylist(value: ByteArray) {
        if (released) return

        value.let {
            if (value.isNotEmpty()) {
                val source: MediaSource = buildVideoToMediaSource(value)
                if (addNewSourceLocker.tryLock()) {
                    concatenatingMediaSource.addMediaSource(source)
                    lastAddedFragmentId++

                    //If the current video fragment is with more than 2 (fragmentsBufferSize) fragments difference than the latest added, then clear the media source and keep only the latest 2 fragments
                    if (lastAddedFragmentId - lastPlayedFragmentId > mediaSourceBufferSize) {
                        concatenatingMediaSource.removeMediaSourceRange(0, concatenatingMediaSource.size - mediaSourceBufferSize)
                        lastPlayedFragmentId = 0
                        lastAddedFragmentId = 0
                        mediaSourceEventsHandler.sendEmptyMessage(CustomPlayerHandler.MSG_PREPARE_PLAYER)
                    } else {
                        mediaSourceEventsHandler.sendEmptyMessage(MSG_FORCE_PLAYER)
                    }
                    synchronized(addNewSourceLocker) {
                        addNewSourceLocker.unlock()
                    }
                }
            }
        }
    }

    private fun buildVideoToMediaSource(value: ByteArray): MediaSource {
        val byteArrayDataSource = com.google.android.exoplayer2.upstream.ByteArrayDataSource(value)
        val factory = com.google.android.exoplayer2.upstream.DataSource.Factory { return@Factory byteArrayDataSource }
        byteArrayDataSource.close()
        return ProgressiveMediaSource.Factory(factory).createMediaSource(Uri.EMPTY)
    }

    /*
        FALLBACK TIMER
    */
    private fun scheduleTimer(delay: Long) {
        mediaSourceEventsHandler.sendEmptyMessageDelayed(MSG_FALLBACK_TIMER, delay)
        isScheduledTask = true
    }

    private fun unScheduleTimer() {
        if (isScheduledTask) {
            mediaSourceEventsHandler.removeMessages(MSG_FALLBACK_TIMER)
            isScheduledTask = false
        }
    }

    private fun isPaused(): Boolean {
        return player.playWhenReady
    }

    private class CustomPlayerHandler(@NonNull val callerContext: DirectStreamingPlayer, @NonNull val player: SimpleExoPlayer, @NonNull val concatenatingMediaSource: ConcatenatingMediaSource) : Handler.Callback {
        companion object {
            const val MSG_PREPARE_PLAYER = 0
            const val MSG_FORCE_PLAYER = 1
            const val MSG_FALLBACK_TIMER = 2
            const val MSG_RELEASE_PLAYER = 3
            const val MSG_PLAYER_RESUME = 4
        }

        var released = false
        var playerPaused = false

        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                MSG_PREPARE_PLAYER -> {
                    if (!released) {
                        player.prepare(concatenatingMediaSource, false, false)
                        player.playWhenReady = true
                    }
                }
                MSG_FORCE_PLAYER -> if (!player.playWhenReady && !released) {
                    player.playWhenReady = true
                }

                MSG_FALLBACK_TIMER -> fallbackIfNotPaused()

                MSG_RELEASE_PLAYER -> release()

                MSG_PLAYER_RESUME -> {
                    playerPaused = false
                    player.playWhenReady = true
                }
            }
            return true
        }

        private fun fallbackIfNotPaused() {
            if (!playerPaused && !released) {
                callerContext.eventListenerWeakReference.get()?.fallBack()
            }
        }

        private fun release() {
            if (!released) {
                released = true
                player.stop()
                player.release()
            }
        }
    }
}