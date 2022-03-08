package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.CameraItem
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentBookmarkExtendedCreateBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper
import com.milestonesys.mobilesdk.bookmarkssample.utils.SnackHelper
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList


open class BookmarkExtendedCreateFragment : Fragment() {
    // This property is only valid between onCreateView and onDestroyView.
    protected val binding get() = _binding!!
    private var _binding: FragmentBookmarkExtendedCreateBinding? = null

    protected val bookmarksViewModel: BookmarksViewModel by activityViewModels()
    private val args: BookmarkExtendedCreateFragmentArgs by navArgs()

    private val today = Calendar.getInstance()
    private val calendarHelper = Calendar.getInstance()
    protected val bookmarkTime: Calendar = Calendar.getInstance()

    private var createQuickBookmark = false

    private var menuItemSave: MenuItem? = null

    private var cameraOptions: ArrayList<CameraItem> = ArrayList()
    protected var timesOptions: List<Int>? = null

    private var selectedCamera: CameraItem? = null
    protected var selectedPreBookmarkTimeIndex = 0
    protected var selectedPostBookmarkTimeIndex = 0

    private val defaultPreBookmarkTime = 3
    private val defaultPostBookmarkTime = 30

    protected fun getSelectedStartTime(): Long {
        val preBookmarkTimeInSeconds = timesOptions!![selectedPreBookmarkTimeIndex].toLong()
        return bookmarkTime.timeInMillis - preBookmarkTimeInSeconds * 1000
    }

    protected fun getSelectedEventTime(): Long {
        return bookmarkTime.timeInMillis
    }

    protected fun getSelectedEndTime(): Long {
        val postBookmarkTimeInSeconds = timesOptions!![selectedPostBookmarkTimeIndex].toLong()
        return bookmarkTime.timeInMillis + postBookmarkTimeInSeconds * 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        timesOptions = resources.getIntArray(R.array.bookmark_times_default_durations).asList()

        args.cameraId?.let {
            createQuickBookmark = true
            bookmarksViewModel.requestBookmarkReference(it)
        }

        setHasOptionsMenu(true)
    }

    open fun loadValues() {
        binding.bookmarkHeadline.editText?.setText(R.string.bookmark_default_headline_detailed)

        if (createQuickBookmark) {
            bookmarksViewModel.createBookmarkData.value?.data?.let { data ->
                selectedCamera = bookmarksViewModel.cameras.value?.data?.find {
                    it.id.toString() == data.cameraId
                }
                binding.bookmarkIdContent.text = data.reference
                binding.cameraNameContent.text = selectedCamera?.name
                binding.bookmarkTimeContent.text = DateHelper.formatted(bookmarkTime.timeInMillis)
                selectedPreBookmarkTimeIndex =
                    timesOptions?.indexOf(data.preBookmarkTime.toInt()) ?: 0
                selectedPostBookmarkTimeIndex =
                    timesOptions?.indexOf(data.postBookmarkTime.toInt()) ?: 0
            }
        } else {
            selectedCamera = bookmarksViewModel.cameras.value?.data?.get(0)
            (binding.bookmarkCamera.editText as? AutoCompleteTextView)?.setText(
                selectedCamera?.name, false
            )
            binding.bookmarkDate.editText?.setText(DateHelper.formattedDateOnly(bookmarkTime.timeInMillis))
            binding.bookmarkTime.editText?.setText(DateHelper.formattedTime24H(bookmarkTime.timeInMillis))
            selectedPreBookmarkTimeIndex = timesOptions?.indexOf(defaultPreBookmarkTime) ?: 0
            selectedPostBookmarkTimeIndex = timesOptions?.indexOf(defaultPostBookmarkTime) ?: 0
        }
        (binding.bookmarkPreBookmarkTime.editText as? AutoCompleteTextView)?.let {
            val item = it.adapter.getItem(selectedPreBookmarkTimeIndex)
            it.setText(item.toString(), false)
        }
        (binding.bookmarkPostBookmarkTime.editText as? AutoCompleteTextView)?.let {
            val item = it.adapter.getItem(selectedPostBookmarkTimeIndex)
            it.setText(item.toString(), false)
        }
    }

    protected fun addToTimeOptions(vararg options: Int) {
        val allTimesOptions = HashSet<Int>()
        timesOptions?.let { allTimesOptions.addAll(it) }
        for (option in options) {
            allTimesOptions.add(option)
        }
        timesOptions = allTimesOptions.sorted()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarkExtendedCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObserver()
    }

    protected open fun setupEditorOptions() {
        if ((binding.bookmarkCamera.editText as? AutoCompleteTextView)?.adapter != null) return

        with(binding.bookmarkPreBookmarkTime.editText as AutoCompleteTextView) {
            setupOptionsFormatted(this, timesOptions)
        }

        with(binding.bookmarkPostBookmarkTime.editText as AutoCompleteTextView) {
            setupOptionsFormatted(this, timesOptions)
        }

        with(binding.bookmarkCamera.editText as AutoCompleteTextView) {
            setupOptions(this, cameraOptions)
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

    private fun setupOptionsFormatted(
        autoCompleteTextView: AutoCompleteTextView?,
        options: List<Int>?
    ) {
        if (autoCompleteTextView == null || options == null) return

        autoCompleteTextView.setAdapter(
            ArrayAdapter(
                autoCompleteTextView.context,
                android.R.layout.simple_spinner_dropdown_item,
                options.map { formatDuration(it) }
            )
        )
    }

    private fun formatDuration(duration: Int): String {
        val hours = duration / 3600
        val minutes = (duration / 60) % 60
        val seconds = duration % 60
        val builder = StringBuilder()
        if (hours > 0) builder.append("$hours h ")
        if (minutes > 0) builder.append("$minutes min ")
        if (seconds > 0 || builder.isEmpty()) builder.append("$seconds sec")
        return builder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        bookmarksViewModel.clearCreateBookmarkData()
    }

    protected open fun setupUI() {

        if (createQuickBookmark) {
            binding.groupNotReadOnlyFields.visibility = View.GONE
            binding.groupReadOnlyFields.visibility = View.VISIBLE
        }

        (binding.bookmarkPreBookmarkTime.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                if (position < timesOptions!!.size) {
                    selectedPreBookmarkTimeIndex = position
                }
            }
        }

        (binding.bookmarkPostBookmarkTime.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                if (position < timesOptions!!.size) {
                    selectedPostBookmarkTimeIndex = position
                }
            }
        }

        (binding.bookmarkCamera.editText as? AutoCompleteTextView)?.let {
            it.setOnItemClickListener { _, _, position, _ ->
                if (position < cameraOptions.size) {
                    selectedCamera = cameraOptions[position]
                }
            }
        }

        binding.bookmarkDate.editText?.setOnFocusChangeListener { _, focus ->
            if (!focus) validateDateInput()
        }

        binding.bookmarkTime.editText?.setOnFocusChangeListener { _, focus ->
            if (!focus) validateTimeInput()
        }

        binding.bookmarkHeadline.editText?.setOnFocusChangeListener { _, focus ->
            if (!focus) validateHeadline()
        }
    }

    private fun validateDateInput() {
        with(binding.bookmarkDate) {
            error = if (obtainValidDateInput(editText!!.text.toString())) {
                null
            } else {
                getString(R.string.error_date_input) + DateHelper.formattedDateOnly(today.timeInMillis)
            }
            updateSaveEnabledState()
        }
    }

    private fun validateTimeInput() {
        with(binding.bookmarkTime) {
            error = if (obtainValidTimeInput(editText!!.text.toString())) {
                null
            } else {
                getString(R.string.error_date_input) + DateHelper.formattedTime24H(today.timeInMillis)
            }
            updateSaveEnabledState()
        }
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
        var hasErrors = false
        if (binding.bookmarkDate.error != null) {
            hasErrors = true
        }
        if (binding.bookmarkTime.error != null) {
            hasErrors = true
        }
        if (binding.bookmarkHeadline.error != null) {
            hasErrors = true
        }
        menuItemSave?.isEnabled = !hasErrors
    }

    protected open fun setupObserver() {
        onLoadingStarted()
        bookmarksViewModel.cameras.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.SUCCESS -> {
                    onDataLoaded()
                }
                DataStatus.ERROR -> {
                    onLoadingComplete()
                    SnackHelper.showMessage(view, R.string.error_cameras)
                }
                else -> {
                }
            }
        })
        if (createQuickBookmark) {
            bookmarksViewModel.createBookmarkData.observe(viewLifecycleOwner, {
                when (it.status) {
                    DataStatus.SUCCESS -> {
                        onDataLoaded()
                    }
                    DataStatus.ERROR -> {
                        onLoadingComplete()
                        SnackHelper.showMessage(view, R.string.error_bookmark_add_disabled)
                    }
                    else -> {
                    }
                }
            })
        }
    }

    protected open fun onDataLoaded() {
        if (createQuickBookmark && bookmarksViewModel.createBookmarkData.value?.data == null) return

        if (bookmarksViewModel.cameras.value?.data == null) return

        onLoadingComplete()
        if (createQuickBookmark) {
            bookmarksViewModel.createBookmarkData.value?.data?.let {
                addToTimeOptions(it.preBookmarkTime.toInt(), it.postBookmarkTime.toInt())
            }
        }
        cameraOptions = arrayListOf()
        bookmarksViewModel.cameras.value?.data?.let { data -> cameraOptions.addAll(data) }
        setupEditorOptions()

        loadValues()
    }

    private fun obtainValidDateInput(input: String): Boolean {
        val date = DateHelper.parseDateInput(input) ?: return false

        calendarHelper.timeInMillis = date

        bookmarkTime.set(Calendar.YEAR, calendarHelper.get(Calendar.YEAR))
        bookmarkTime.set(Calendar.DAY_OF_YEAR, calendarHelper.get(Calendar.DAY_OF_YEAR))
        return true
    }

    private fun obtainValidTimeInput(input: String): Boolean {
        try {
            val parts = input.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val second = parts[2].toInt()
            if (
                hour < 0 || hour > 23 ||
                minute < 0 || minute > 59 ||
                second < 0 || second > 59
            ) return false

            bookmarkTime.set(Calendar.HOUR_OF_DAY, hour)
            bookmarkTime.set(Calendar.MINUTE, minute)
            bookmarkTime.set(Calendar.SECOND, second)

        } catch (e: Exception) {
            return false
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_changes, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menuItemSave = menu.findItem(R.id.menu_item_save)
    }

    open fun onSaveSelected() {
        if (bookmarksViewModel.isCreateEnabledForCamera(selectedCamera?.id)) {
            createDetailedBookmark()
        } else {
            SnackHelper.showMessage(view, R.string.error_bookmark_add_disabled)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_save) {
            if (!createQuickBookmark) {
                validateDateInput()
                validateTimeInput()
            }
            validateHeadline()
            if (!item.isEnabled) return true

            onSaveSelected()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createDetailedBookmark() {
        val cameraId = selectedCamera!!.id.toString()
        val headline = binding.bookmarkHeadline.editText!!.text.toString()
        val description = binding.bookmarkDescription.editText!!.text.toString()
        val reference = if (createQuickBookmark) binding.bookmarkIdContent.text.toString() else null

        onLoadingStarted()

        bookmarksViewModel.createBookmark(
            cameraId,
            headline,
            getSelectedEventTime(),
            description,
            getSelectedStartTime(),
            getSelectedEndTime(),
            reference,
            object : BookmarksViewModel.OperationCallback {
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

    protected fun onLoadingStarted() {
        menuItemSave?.isEnabled = false
        binding.progressIndicator.visibility = View.VISIBLE
        binding.mainLayout.children.forEach { it.isEnabled = false }
    }

    protected fun onLoadingComplete() {
        menuItemSave?.isEnabled = true
        binding.progressIndicator.visibility = View.GONE
        binding.mainLayout.children.forEach { it.isEnabled = true }
    }
}