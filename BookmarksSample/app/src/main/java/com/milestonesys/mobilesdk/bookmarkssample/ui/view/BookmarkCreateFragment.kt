package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.transition.platform.*
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.CameraItem
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentBookmarkCreateBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.SnackHelper
import java.util.*

class BookmarkCreateFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentBookmarkCreateBinding? = null

    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()

    private var menuItemSave: MenuItem? = null

    private var selectedBookmarkType = 0
    private var selectedCamera: CameraItem? = null

    private var typeOptions: List<String>? = null
    private var cameraOptions: ArrayList<CameraItem> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        typeOptions = resources.getStringArray(R.array.bookmarks_create_type_values).asList()

        activity?.setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        sharedElementEnterTransition = MaterialContainerTransform()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarkCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObserver()
    }

    private fun setupUI() {

        binding.bookmarkHeadline.editText?.setText(R.string.bookmark_default_headline_quick)

        (binding.bookmarkType.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                selectedBookmarkType = position
                binding.bookmarkHeadline.visibility = if (position == 0) View.VISIBLE else View.GONE
                binding.bookmarkCamera.visibility = if (position == 2) View.GONE else View.VISIBLE
                binding.buttonAddDetails.visibility = if (position == 0) View.GONE else View.VISIBLE
                menuItemSave?.isVisible = position == 0
            }
        }

        binding.buttonAddDetails.setOnClickListener {
            onAddDetailsClick()
        }

        (binding.bookmarkCamera.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                if (position < cameraOptions.size) {
                    selectedCamera = cameraOptions[position]
                }
            }
        }

        binding.bookmarkHeadline.editText?.setOnFocusChangeListener { _, focus ->
            if (!focus) validateHeadline()
        }
    }

    private fun setupObserver() {
        bookmarksViewModel.cameras.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.LOADING -> {
                    onLoadingStarted()
                }
                DataStatus.SUCCESS -> {
                    onLoadingComplete()
                    cameraOptions = arrayListOf()
                    it.data?.let { data -> cameraOptions.addAll(data) }
                    setupOptions()
                }
                DataStatus.ERROR -> {
                    onLoadingComplete()
                    SnackHelper.showMessage(view, R.string.error_cameras)
                }
                else -> {
                }
            }
        })
    }

    private fun setupOptions() {
        if ((binding.bookmarkCamera.editText as? AutoCompleteTextView)?.adapter != null) return

        with(binding.bookmarkType.editText as AutoCompleteTextView) {
            setupOptions(this, typeOptions)
            selectedBookmarkType = 0
            setText(typeOptions?.get(selectedBookmarkType), false)
        }

        with(binding.bookmarkCamera.editText as AutoCompleteTextView) {
            setupOptions(this, cameraOptions)
            selectedCamera = cameraOptions[0]
            setText(selectedCamera?.name, false)
        }
    }

    private fun setupOptions(autoCompleteTextView: AutoCompleteTextView?, options: List<Any>?) {
        if (autoCompleteTextView == null || options == null) return

        autoCompleteTextView.setAdapter(
            ArrayAdapter(
                autoCompleteTextView.context,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_changes, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menuItemSave = menu.findItem(R.id.menu_item_save)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_save) {
            validateHeadline()
            if (!item.isEnabled) return true

            onSaveSelected()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun validateHeadline() {
        with(binding.bookmarkHeadline) {
            error = if (editText!!.text.isNotBlank()) {
                null
            } else {
                getString(R.string.error_headline_required)
            }
            updateSaveEnabledState()
        }
    }

    private fun updateSaveEnabledState() {
        menuItemSave?.isEnabled = binding.bookmarkHeadline.error == null
    }

    private fun onSaveSelected() {
        if (bookmarksViewModel.isCreateEnabledForCamera(selectedCamera?.id)) {
            createQuickBookmark()
        } else {
            SnackHelper.showMessage(view, R.string.error_bookmark_add_disabled)
        }
    }

    private fun onAddDetailsClick() {
        when (selectedBookmarkType) {
            1 -> createDetailedBookmarkLive()
            2 -> createDetailedBookmarkPlayback()
        }
    }

    private fun createQuickBookmark() {
        val cameraId = selectedCamera!!.id.toString()
        val bookmarkHeadline = binding.bookmarkHeadline.editText!!.text.toString()
        val bookmarkTime = System.currentTimeMillis()

        onLoadingStarted()

        bookmarksViewModel.createBookmark(
            cameraId,
            bookmarkHeadline,
            bookmarkTime,
            callback = object : BookmarksViewModel.OperationCallback {
                override fun onOperationEnded(result: Boolean) {
                    if (view == null) return

                    onLoadingComplete()
                    if (result) {
                        findNavController().popBackStack()
                    }
                }
            }
        )
    }

    private fun createDetailedBookmarkLive() {
        val cameraId = selectedCamera!!.id.toString()

        findNavController().navigate(BookmarkCreateFragmentDirections
            .actionBookmarkCreateFragmentToBookmarkExtendedCreateFragment(cameraId),
            navOptions {
                popUpTo = R.id.bookmarksListFragment
            }
        )
    }

    private fun createDetailedBookmarkPlayback() {

        findNavController().navigate(BookmarkCreateFragmentDirections
            .actionBookmarkCreateFragmentToBookmarkExtendedCreateFragment(),
            navOptions {
                popUpTo = R.id.bookmarksListFragment
            }
        )
    }

    private fun onLoadingStarted() {
        menuItemSave?.isEnabled = false
        binding.progressIndicator.visibility = View.VISIBLE
        binding.mainLayout.children.forEach { it.isEnabled = false }
    }

    private fun onLoadingComplete() {
        menuItemSave?.isEnabled = true
        binding.progressIndicator.visibility = View.GONE
        binding.mainLayout.children.forEach { it.isEnabled = true }
    }
}