package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkItem
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentBookmarkDetailsBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper

class BookmarkDetailsFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentBookmarkDetailsBinding? = null

    private lateinit var currentBookmark: BookmarkItem

    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()

    private var imageMaxWidth = 0
    private var imageMaxHeight = 0
    private var streamRequested = false

    private var menuItemEdit: MenuItem? = null
    private var menuItemDelete: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        sharedElementEnterTransition = MaterialContainerTransform()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarkDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        bookmarksViewModel.stopVideoStream()
    }

    override fun onPause() {
        super.onPause()
        pauseVideo()
        binding.videoView.notifyOnVideoPaused()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObserver()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        imageMaxWidth = resources.displayMetrics.widthPixels
        imageMaxHeight = resources.getDimension(R.dimen.video_height).toInt()

        binding.videoView.updateMaxDimens(imageMaxWidth, imageMaxHeight)
        bookmarksViewModel.resizeVideoStream(imageMaxWidth, imageMaxHeight)
    }

    private fun setupUI() {

        with(binding.videoView) {
            onPlayClickListener = View.OnClickListener { playVideo() }
            onPauseClickListener = View.OnClickListener { pauseVideo() }
            onReplayClickListener = View.OnClickListener { replayVideo() }
        }

        binding.buttonEmptyList.setOnClickListener { bookmarksViewModel.refreshSelectedBookmark() }

        imageMaxWidth = resources.displayMetrics.widthPixels
        imageMaxHeight = resources.getDimension(R.dimen.video_height).toInt()

        binding.videoView.updateMaxDimens(imageMaxWidth, imageMaxHeight)
        requestVideoStream()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_details, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menuItemEdit = menu.findItem(R.id.menu_item_edit)
        menuItemEdit?.isEnabled = bookmarksViewModel.selectedBookmark.value?.data != null

        menuItemDelete = menu.findItem(R.id.menu_item_delete)
        menuItemDelete?.isEnabled = bookmarksViewModel.selectedBookmark.value?.data != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!this::currentBookmark.isInitialized) return super.onOptionsItemSelected(item)

        val camera =
            bookmarksViewModel.cameras.value?.data?.find { it.id == currentBookmark.cameraId }
                ?: return super.onOptionsItemSelected(item)

        if (item.itemId == R.id.menu_item_edit) {
            if (camera.editBookmarksEnabled) {
                navigateToEditBookmark()
            } else {
                Snackbar.make(
                    requireView(),
                    getString(R.string.error_bookmark_update_disabled),
                    Snackbar.LENGTH_LONG
                ).show()
            }
            return true
        }
        if (item.itemId == R.id.menu_item_delete) {
            if (camera.deleteBookmarksEnabled) {
                showDeleteDialog()
            } else {
                Snackbar.make(
                    requireView(),
                    getString(R.string.error_bookmark_delete_disabled),
                    Snackbar.LENGTH_LONG
                ).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun navigateToEditBookmark() {
        findNavController().navigate(
            BookmarkDetailsFragmentDirections
                .actionBookmarkDetailsFragmentToBookmarkEditFragment()
        )
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.bookmark_details_delete_button_confirm)) { _, _ ->
                deleteBookmark()
            }
            .setNegativeButton(getString(R.string.bookmark_details_delete_button_cancel)) { _, _ ->
            }
            .setMessage(getString(R.string.bookmark_details_delete_confirm_message))
            .show()
    }

    private fun deleteBookmark() {
        onDeleteStarted()
        bookmarksViewModel.deleteBookmark(
            bookmarksViewModel.selectedBookmark.value!!.data?.id.toString(),
            object : BookmarksViewModel.OperationCallback {
                override fun onOperationEnded(result: Boolean) {
                    if (view == null) return

                    onDeleteComplete()
                    if (result) {
                        findNavController().popBackStack()
                    }
                }
            }
        )
    }

    private fun onDeleteStarted() {
        changeButtonsEnabled(false)
        binding.groupLoading.visibility = View.VISIBLE
    }

    private fun onDeleteComplete() {
        changeButtonsEnabled(true)
        binding.groupLoading.visibility = View.GONE
    }

    private fun changeButtonsEnabled(isEnabled: Boolean) {
        menuItemEdit?.isEnabled = isEnabled
        menuItemDelete?.isEnabled = isEnabled
    }

    private fun replayVideo() {
        restartStream(true)
    }

    private fun restartStream(autoPlay: Boolean) {
        binding.videoView.notifyOnLoading(autoPlay)
        bookmarksViewModel.restartVideoStream(autoPlay)
    }

    private fun playVideo() {
        bookmarksViewModel.playCurrentStream()
    }

    private fun pauseVideo() {
        bookmarksViewModel.pauseCurrentStream()
    }

    private fun setupObserver() {
        bookmarksViewModel.currentFrame.observe(viewLifecycleOwner, {
            binding.videoView.frame = it
        })
        bookmarksViewModel.videoEnded.observe(viewLifecycleOwner, {
            if (it == true) binding.videoView.notifyOnVideoEnded()
        })
        bookmarksViewModel.videoError.observe(viewLifecycleOwner, {
            if (it == true) binding.videoView.notifyOnVideoError()
        })

        bookmarksViewModel.selectedBookmark.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.LOADING -> {
                    changeButtonsEnabled(false)
                    binding.groupLoading.visibility = View.VISIBLE
                    binding.groupMainContent.visibility = View.GONE
                    binding.groupEmpty.visibility = View.GONE
                }
                DataStatus.SUCCESS -> {
                    changeButtonsEnabled(true)
                    binding.groupLoading.visibility = View.GONE
                    binding.groupMainContent.visibility = View.VISIBLE
                    binding.groupEmpty.visibility = View.GONE
                    onDataLoaded(it.data)
                }
                DataStatus.ERROR -> {
                    changeButtonsEnabled(false)
                    binding.groupLoading.visibility = View.GONE
                    binding.groupMainContent.visibility = View.GONE
                    binding.groupEmpty.visibility = View.VISIBLE
                }
                else -> {
                }
            }
        })
    }

    private fun onDataLoaded(bookmarkItem: BookmarkItem?) {
        currentBookmark = bookmarkItem ?: return
        binding.bookmarkName.text = bookmarkItem.name
        binding.bookmarkEventTime.text = DateHelper.formatted(bookmarkItem.eventTime)
        binding.bookmarkDescription.text = bookmarkItem.description
        binding.timeIntervalStartContent.text = DateHelper.formatted(bookmarkItem.startTime)
        binding.timeIntervalEndContent.text = DateHelper.formatted(bookmarkItem.endTime)
        binding.informationIdContent.text = bookmarkItem.reference
        binding.informationAuthorContent.text = bookmarkItem.username
        binding.informationCameraContent.text = bookmarkItem.cameraName

        requestVideoStream()
    }

    private fun requestVideoStream() {
        if (!this::currentBookmark.isInitialized || imageMaxHeight == 0) return

        if (streamRequested) {
            restartStream(false)
        } else {
            streamRequested = true
            bookmarksViewModel.startVideoStream(imageMaxWidth, imageMaxHeight)
        }
    }
}