package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.FrameItem
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper

class VideoImageView : FrameLayout {

    var onPlayClickListener: OnClickListener? = null
    var onPauseClickListener: OnClickListener? = null
    var onReplayClickListener: OnClickListener? = null

    private lateinit var imageViewFrame: ImageView
    private lateinit var textViewTime: TextView
    private lateinit var videoButton: ImageButton
    private lateinit var errorView: TextView
    private lateinit var progressIndicator: CircularProgressIndicator

    private var maxWidth = 0
    private var maxHeight = 0
    private var playDrawable: Drawable? = null
    private var pauseDrawable: Drawable? = null
    private var replayDrawable: Drawable? = null
    private var autoPlay: Boolean = false
    private var videoButtonState: VideoButtonState = VideoButtonState.PLAY
        set(value) {
            field = value
            when (value) {
                VideoButtonState.PLAY -> videoButton.background = playDrawable
                VideoButtonState.PAUSE -> videoButton.background = pauseDrawable
                VideoButtonState.REPLAY -> videoButton.background = replayDrawable
            }
        }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        inflate(context, R.layout.view_video, this)

        imageViewFrame = findViewById(R.id.imageBookmarkVideo)
        imageViewFrame.setOnClickListener { changeOverlayVisibility() }
        textViewTime = findViewById(R.id.time)

        videoButton = findViewById(R.id.buttonBookmarkVideo)
        videoButton.setOnClickListener {
            onVideoButtonClicked(it as ImageButton)
        }

        errorView = findViewById(R.id.textViewErrorMessage)
        progressIndicator = findViewById(R.id.progressIndicator)

        playDrawable = ResourcesCompat.getDrawable(
            context.resources, R.drawable.ic_play_video, context.theme
        )

        pauseDrawable = ResourcesCompat.getDrawable(
            context.resources, R.drawable.ic_pause_video, context.theme
        )

        replayDrawable = ResourcesCompat.getDrawable(
            context.resources, R.drawable.ic_replay_video, context.theme
        )
    }

    fun notifyOnVideoPaused() {
        if (videoButtonState == VideoButtonState.PAUSE) {
            videoButtonState = VideoButtonState.PLAY
        }
    }

    fun notifyOnVideoEnded() {
        videoButtonState = VideoButtonState.REPLAY
        changeOverlayVisibility(true)
    }

    fun notifyOnVideoError() {
        progressIndicator.visibility = GONE

        errorView.visibility = View.VISIBLE

        videoButton.visibility = View.GONE
        textViewTime.visibility = View.GONE
    }

    fun notifyOnLoading(autoPlay: Boolean) {
        progressIndicator.visibility = VISIBLE

        errorView.visibility = View.GONE

        videoButton.visibility = View.GONE
        textViewTime.visibility = View.GONE

        this.autoPlay = autoPlay
    }

    private fun showFrame(frame: FrameItem) {
        imageViewFrame.setImageBitmap(frame.image)
        textViewTime.text = DateHelper.formattedTimeOnly(frame.time)

        progressIndicator.visibility = GONE

        errorView.visibility = View.GONE

        if (videoButton.visibility == View.GONE) {
            videoButtonState = if (autoPlay) VideoButtonState.PAUSE else VideoButtonState.PLAY
            videoButton.visibility = View.VISIBLE
            textViewTime.visibility = View.VISIBLE
        }

        updateImageParams()
    }

    private fun changeOverlayVisibility(forceShow: Boolean = false) {
        if (videoButton.visibility == View.INVISIBLE || forceShow) {
            videoButton.visibility = View.VISIBLE
            textViewTime.visibility = View.VISIBLE
        } else {
            videoButton.visibility = View.INVISIBLE
            textViewTime.visibility = View.INVISIBLE
        }
    }

    private fun onVideoButtonClicked(button: ImageButton) {
        videoButtonState = when (videoButtonState) {
            VideoButtonState.PLAY -> {
                onPlayClickListener?.onClick(button)
                VideoButtonState.PAUSE
            }
            VideoButtonState.PAUSE -> {
                onPauseClickListener?.onClick(button)
                VideoButtonState.PLAY
            }
            VideoButtonState.REPLAY -> {
                onReplayClickListener?.onClick(button)
                VideoButtonState.PAUSE
            }
        }
    }

    fun updateMaxDimens(maxWidth: Int, maxHeight: Int) {
        this.maxWidth = maxWidth
        this.maxHeight = maxHeight

        updateImageParams()
    }

    var frame: FrameItem? = null
        set(value) {
            field = value
            value?.let {
                showFrame(it)
            }
        }

    private fun updateImageParams() {
        val image = (imageViewFrame.drawable as? BitmapDrawable)?.bitmap ?: return
        val params = imageViewFrame.layoutParams
        val widthScale = maxWidth.toFloat() / image.width
        val heightScale = maxHeight.toFloat() / image.height
        val minScale = widthScale.coerceAtMost(heightScale)
        params.height = (minScale * image.height).toInt()
        params.width = (minScale * image.width).toInt()
        imageViewFrame.layoutParams = params
    }

    enum class VideoButtonState {
        PLAY, PAUSE, REPLAY
    }
}