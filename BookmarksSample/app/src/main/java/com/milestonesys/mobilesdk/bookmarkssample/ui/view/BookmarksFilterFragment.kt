package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.CameraItem
import com.milestonesys.mobilesdk.bookmarkssample.data.model.FilterModel
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentBookmarksFilterBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.EdgeToEdgeHelper
import com.milestonesys.mobilesdk.bookmarkssample.utils.SnackHelper
import java.util.UUID

class BookmarksFilterFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentBookmarksFilterBinding? = null

    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()

    private val emptyId = "00000000-0000-0000-0000-000000000000"
    private lateinit var emptyCamera: CameraItem

    private var filterByTimeOptions: List<String>? = null
    private var filterByCameraOptions: ArrayList<CameraItem> = ArrayList()
    private var filterByAuthorOptions: List<String>? = null

    private var selectedTimeIntervalIndex = 0
    private var selectedCamera: CameraItem? = null
    private var selectedAuthorIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        filterByTimeOptions =
            resources.getStringArray(R.array.bookmarks_filter_by_time_values).asList()
        filterByAuthorOptions =
            resources.getStringArray(R.array.bookmarks_filter_by_author_values).asList()

        emptyCamera = CameraItem(
            UUID.fromString(emptyId),
            resources.getString(R.string.bookmarks_filter_by_camera_value_any)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarksFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EdgeToEdgeHelper.setupEdgeToEdgeContentInsets(binding.root)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_filter, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.menu_item_apply) {
                    applyFilter()
                    findNavController().popBackStack()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setupUI()
        setupObserver()
    }

    private fun setupUI() {

        binding.filterByText.editText?.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) hideKeyboard(view)
        }

        (binding.filterByTime.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                selectedTimeIntervalIndex = position
            }
        }
        (binding.filterByCamera.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                if (position < filterByCameraOptions.size) {
                    selectedCamera = filterByCameraOptions[position]
                }
            }
        }
        (binding.filterByAuthor.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                selectedAuthorIndex = position
            }
        }

        binding.buttonReset.setOnClickListener {
            loadValuesFromModel(FilterModel())
        }
    }

    private fun setupOptions() {
        if ((binding.filterByTime.editText as? AutoCompleteTextView)?.adapter != null) return

        setupOptions(binding.filterByTime.editText as? AutoCompleteTextView, filterByTimeOptions)
        setupOptions(
            binding.filterByCamera.editText as? AutoCompleteTextView,
            filterByCameraOptions
        )
        setupOptions(
            binding.filterByAuthor.editText as? AutoCompleteTextView,
            filterByAuthorOptions
        )

        loadValuesFromModel(bookmarksViewModel.filter)
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

    private fun hideKeyboard(view: View) {
        activity?.let {
            with(it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager) {
                hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun loadValuesFromModel(viewModel: FilterModel) {
        selectedCamera = viewModel.camera ?: emptyCamera
        selectedTimeIntervalIndex = viewModel.timeIntervalIndex
        selectedAuthorIndex = if (viewModel.mineOnly) 1 else 0

        binding.filterByText.editText?.setText(viewModel.text)

        (binding.filterByCamera.editText as? AutoCompleteTextView)
            ?.setText(selectedCamera?.name, false)

        filterByTimeOptions?.let {
            if (it.size > selectedTimeIntervalIndex) {
                (binding.filterByTime.editText as? AutoCompleteTextView)
                    ?.setText(it[selectedTimeIntervalIndex], false)
            }
        }

        filterByAuthorOptions?.let {
            if (it.size > selectedAuthorIndex) {
                (binding.filterByAuthor.editText as? AutoCompleteTextView)
                    ?.setText(it[selectedAuthorIndex], false)
            }
        }
    }

    private fun setupObserver() {
        onLoadingStarted()
        bookmarksViewModel.cameras.observe(viewLifecycleOwner) {
            when (it?.status) {
                DataStatus.SUCCESS -> {
                    onLoadingComplete()
                    filterByCameraOptions = arrayListOf(emptyCamera)
                    it.data?.let { data -> filterByCameraOptions.addAll(data) }
                    setupOptions()
                }

                DataStatus.ERROR -> {
                    onLoadingComplete()
                    SnackHelper.showMessage(view, R.string.error_cameras)
                }

                else -> {
                }
            }
        }
    }

    private fun onLoadingStarted() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.groupAllFields.visibility = View.GONE
    }

    private fun onLoadingComplete() {
        binding.progressIndicator.visibility = View.GONE
        binding.groupAllFields.visibility = View.VISIBLE
    }

    private fun applyFilter() {
        val text = with(binding.filterByText.editText!!.text) {
            if (isBlank()) null else toString()
        }
        bookmarksViewModel.filter = FilterModel(
            text,
            selectedTimeIntervalIndex,
            if (selectedCamera?.id.toString() != emptyId) selectedCamera else null,
            selectedAuthorIndex != 0
        )
    }
}
