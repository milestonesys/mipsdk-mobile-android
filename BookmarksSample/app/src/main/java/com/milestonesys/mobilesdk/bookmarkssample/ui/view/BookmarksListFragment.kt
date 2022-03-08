package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.platform.MaterialElevationScale
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.data.model.BookmarkItem
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentBookmarksListBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.adapter.BookmarksAdapter
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus
import com.milestonesys.mobilesdk.bookmarkssample.utils.DateHelper
import java.lang.StringBuilder

class BookmarksListFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentBookmarksListBinding? = null

    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()

    private lateinit var bookmarksAdapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialFadeThrough()

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarksListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        setupUI()
        setupObserver()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_logout) {
            logOut()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupUI() {

        val clickListener = object : BookmarksAdapter.BookmarkClickListener {
            override fun onClick(view: View, position: Int) {
                onBookmarkClicked(view, position)
            }
        }

        val loadingListener = object : BookmarksAdapter.BookmarkLoadingListener {
            override fun onLoadingRequested(position: Int) {
                onBookmarkLoadingRequested(position)
            }
        }

        bookmarksAdapter = BookmarksAdapter(ArrayList(), clickListener, loadingListener)

        binding.textViewFiltersSummary.text = makeFilterSummary()
        binding.buttonChangeFilters.setOnClickListener { navigateToFilters() }
        if (bookmarksViewModel.bookmarksEnabled) {
            binding.buttonCreateBookmark.also {
                it.visibility = View.VISIBLE
                it.setOnClickListener { navigateToCreateNew(it) }
            }
        }
        binding.swipeRefreshLayout.setOnRefreshListener { bookmarksViewModel.refreshBookmarks() }

        binding.recyclerView.also {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = bookmarksAdapter
            it.addItemDecoration(
                DividerItemDecoration(
                    binding.recyclerView.context,
                    (binding.recyclerView.layoutManager as LinearLayoutManager).orientation
                )
            )
        }
    }

    private fun setupObserver() {
        bookmarksViewModel.bookmarks.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.LOADING -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.groupMainContent.visibility = View.GONE
                }
                DataStatus.SUCCESS -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.groupMainContent.visibility = View.VISIBLE
                    renderList(it.data)
                }
                DataStatus.ERROR -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    renderList(it.data)
                }
                else -> {
                }
            }
        })
    }

    private fun onBookmarkLoadingRequested(position: Int) {
        bookmarksViewModel.loadBookmarksAfterPosition(position)
    }

    private fun onBookmarkClicked(view: View, position: Int) {
        bookmarksViewModel.selectBookmarkAt(position)

        val action =
            BookmarksListFragmentDirections
                .actionBookmarksListFragmentToBookmarkDetailsFragment()

        val detailsTransitionName =
            getString(R.string.details_transition_name)

        val extras: FragmentNavigator.Extras =
            FragmentNavigatorExtras(view to detailsTransitionName)

        findNavController().navigate(action, extras)
    }

    private fun navigateToCreateNew(clickedView: View) {

        val action =
            BookmarksListFragmentDirections
                .actionBookmarksListFragmentToBookmarkCreateFragment()

        val createTransitionName =
            getString(R.string.create_transition_name)

        val extras: FragmentNavigator.Extras =
            FragmentNavigatorExtras(clickedView to createTransitionName)

        findNavController().navigate(action, extras)
    }

    private fun navigateToFilters() {

        findNavController().navigate(
            BookmarksListFragmentDirections.actionBookmarksListFragmentToBookmarksFilterFragment()
        )
    }

    private fun logOut() {
        bookmarksViewModel.logOut()

        val navController = findNavController()
        val startDestination = navController.graph.startDestination

        navController.navigate(
            startDestination,
            null,
            navOptions {
                popUpTo = navController.graph.id
            }
        )
    }

    private fun reloadBookmarks() {
        bookmarksViewModel.refreshBookmarks(true)
    }

    private fun makeFilterSummary(): String {
        val summary = StringBuilder()
        with(bookmarksViewModel.filter) {
            if (text != null) {
                summary.appendLine(text)
            }
            if (timeIntervalStart != null && timeIntervalEnd != null) {
                summary.appendLine(
                    DateHelper.formatted(timeIntervalStart!!) +
                            " - " +
                            DateHelper.formatted(timeIntervalEnd!!)
                )
            }
            if (camera != null) {
                summary.appendLine(camera.name)
            }
            if (mineOnly) {
                summary.appendLine(
                    getString(
                        R.string.bookmarks_list_filters_summary_added_by, bookmarksViewModel.user
                    )
                )
            }
        }
        if (summary.isBlank()) {
            summary.appendLine(getString(R.string.bookmarks_list_filters_summary_empty))
        }
        return summary.toString()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun renderList(bookmarks: List<BookmarkItem>?) {

        if (bookmarks.isNullOrEmpty()) {
            updateEmptyContent()
            if (binding.groupEmptyContent.visibility != View.VISIBLE) {
                binding.groupEmptyContent.visibility = View.VISIBLE
            }
        } else {
            if (binding.groupEmptyContent.visibility == View.VISIBLE) {
                binding.groupEmptyContent.visibility = View.GONE
            }
        }
        bookmarksAdapter.replaceData(
            bookmarks ?: ArrayList(),
            bookmarksViewModel.allBookmarksLoaded
        )
        bookmarksAdapter.notifyDataSetChanged()
    }

    private fun updateEmptyContent() {
        if (bookmarksViewModel.bookmarks.value?.status == DataStatus.ERROR) {
            // An error has occurred while loading the bookmarks:
            binding.textViewEmptyList.text = getString(R.string.bookmarks_list_empty_error)
            binding.buttonEmptyList.text =
                getString(R.string.bookmarks_list_empty_button_text_retry)
            binding.buttonEmptyList.setOnClickListener { reloadBookmarks() }
        } else if (!bookmarksViewModel.bookmarksEnabled) {
            // The feature is disabled:
            binding.textViewEmptyList.text =
                getString(R.string.bookmarks_list_empty_feature_disabled)
            binding.buttonEmptyList.text =
                getString(R.string.bookmarks_list_empty_button_text_another)
            binding.buttonEmptyList.setOnClickListener { logOut() }
        } else if (bookmarksViewModel.filter.isNotEmpty()) {
            // The list is empty due to the applied filter:
            binding.textViewEmptyList.text = getString(R.string.bookmarks_list_empty_with_filters)
            binding.buttonEmptyList.text =
                getString(R.string.bookmarks_list_empty_button_text_with_filters)
            binding.buttonEmptyList.setOnClickListener { navigateToFilters() }
        } else {
            // The list is empty due to fact that no bookmarks exist on the server:
            binding.textViewEmptyList.text = getString(R.string.bookmarks_list_empty_no_filters)
            binding.buttonEmptyList.text =
                getString(R.string.bookmarks_list_empty_button_text_no_filters)
            binding.buttonEmptyList.setOnClickListener { navigateToCreateNew(it) }
        }
    }
}