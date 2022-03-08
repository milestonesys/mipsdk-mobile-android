package com.milestonesys.mobilesdk.audiosample.ui.view

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.exoplayer2.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.audiosample.R
import com.milestonesys.mobilesdk.audiosample.databinding.FragmentCameraDetailsBinding
import com.milestonesys.mobilesdk.audiosample.ui.viewmodel.CamerasViewModel
import com.milestonesys.mobilesdk.audiosample.utils.DataStatus


class CameraDetailsFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentCameraDetailsBinding? = null

    private var audioOnDrawable: Drawable? = null
    private var audioOffDrawable: Drawable? = null
    private var buttonPlayText: String? = null
    private var buttonStopText: String? = null

    private val camerasViewModel: CamerasViewModel by activityViewModels()

    private var player: ExoPlayer? = null
    private var isPlaying = false
    private var playingItem: MediaItem? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialFadeThrough()
        sharedElementEnterTransition = MaterialContainerTransform()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentCameraDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObserver()
    }

    private fun initializePlayer() {
        player = SimpleExoPlayer.Builder(requireContext()).build()
        player?.playWhenReady = true

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    enableUI(true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                view?.let {
                    Snackbar.make(
                        it,
                        getString(R.string.error_play_audio),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                isPlaying = false
                enableUI(false)
            }
        }
        player?.addListener(playerListener!!)
    }

    private fun releasePlayer() {
        player?.release()
        playerListener?.let {
            player?.removeListener(it)
        }
        playerListener = null
        player = null
    }

    private fun playCurrentItem() {
        playingItem?.let {
            player?.setMediaItem(it)
            player?.prepare()
        }
    }

    override fun onStart() {
        super.onStart()

        initializePlayer()
    }

    override fun onStop() {
        super.onStop()

        if (isPlaying) {
            onStopRequested()
            isPlaying = false
        }
        releasePlayer()
    }

    private fun onAudioClick() {
        if (isPlaying) {
            onStopRequested()
        } else {
            onPlayRequested()
        }
        isPlaying = !isPlaying
    }

    private fun onPlayRequested() {
        disableUI()

        camerasViewModel.playAudio()
    }

    private fun onStopRequested() {
        player?.stop()
        camerasViewModel.stopAudio()

        enableUI(false)
    }

    private fun disableUI() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.imageViewSpeaker.visibility = View.INVISIBLE
        binding.buttonPlayAudio.isEnabled = false
    }

    private fun enableUI(isPlaying: Boolean) {
        binding.progressIndicator.visibility = View.INVISIBLE
        binding.imageViewSpeaker.visibility = View.VISIBLE
        binding.buttonPlayAudio.isEnabled = true

        if (isPlaying) {
            binding.buttonPlayAudio.text = buttonStopText
            binding.imageViewSpeaker.setImageDrawable(audioOnDrawable)
        } else {
            binding.buttonPlayAudio.text = buttonPlayText
            binding.imageViewSpeaker.setImageDrawable(audioOffDrawable)
        }
    }

    private fun setupUI() {
        binding.buttonPlayAudio.setOnClickListener {
            onAudioClick()
        }

        audioOnDrawable = ResourcesCompat.getDrawable(
            requireContext().resources, R.drawable.ic_all_sources, requireContext().theme)

        audioOffDrawable = ResourcesCompat.getDrawable(
            requireContext().resources, R.drawable.ic_mute, requireContext().theme)

        buttonPlayText = getString(R.string.button_play)
        buttonStopText = getString(R.string.button_stop)
    }

    private fun setupObserver() {
        camerasViewModel.selectedCamera.observe(viewLifecycleOwner, {
            binding.textViewCameraName.text = it.cameraName
            binding.textViewMicName.text = it.micName

            if (!it.micLiveAudio) {
                binding.buttonPlayAudio.isEnabled = false
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_play_not_supported),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        })
        camerasViewModel.audioUrl.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.SUCCESS -> {
                    playingItem = MediaItem.fromUri(it.data.toString())
                    playCurrentItem()
                }
                DataStatus.ERROR -> {
                    view?.let {
                        v -> Snackbar.make(v, it.message!!, Snackbar.LENGTH_LONG).show()
                    }
                    onStopRequested()
                    isPlaying = false
                }
                else -> { }
            }
        })
    }
}