package com.milestonesys.mobilesdk.audiosample.ui.view

import android.os.Bundle
import android.view.*
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.databinding.FragmentCameraListBinding
import com.milestonesys.mobilesdk.audiosample.ui.adapter.CamerasAdapter
import com.milestonesys.mobilesdk.audiosample.ui.adapter.CamerasAdapter.CameraClickListener
import com.milestonesys.mobilesdk.audiosample.ui.viewmodel.CamerasViewModel
import com.milestonesys.mobilesdk.audiosample.utils.DataStatus
import kotlin.collections.ArrayList
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.audiosample.R


class CamerasListFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentCameraListBinding? = null

    private val camerasViewModel: CamerasViewModel by activityViewModels()

    private lateinit var camerasAdapter: CamerasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialFadeThrough()
        enterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentCameraListBinding.inflate(inflater, container, false)
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

    private fun onCameraClicked(view: View, position: Int) {
        camerasViewModel.selectCameraAt(position)

        val action =
            CamerasListFragmentDirections
                .actionCamerasListFragmentToCameraDetailFragment()

        val detailsTransitionName =
            getString(R.string.details_transition_name)

        val extras: FragmentNavigator.Extras =
            FragmentNavigatorExtras(view to detailsTransitionName)

        findNavController().navigate(action, extras)
    }

    private fun setupUI() {

        val clickListener = object : CameraClickListener {
            override fun onClick(view: View, position: Int) {
                onCameraClicked(view, position)
            }
        }

        camerasAdapter = CamerasAdapter(ArrayList(), clickListener)

        binding.recyclerView.also {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = camerasAdapter
            it.addItemDecoration(
                DividerItemDecoration(
                    binding.recyclerView.context,
                    (binding.recyclerView.layoutManager as LinearLayoutManager).orientation
                )
            )
        }
    }

    private fun setupObserver() {
        camerasViewModel.cameras.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.SUCCESS -> {
                    binding.progressIndicator.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    renderList(it.data)
                }
                DataStatus.LOADING -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                }
                DataStatus.ERROR -> {
                    //Handle Error
                    binding.progressIndicator.visibility = View.GONE
                    Snackbar.make(requireView(), "it.message", Snackbar.LENGTH_LONG).show()
                }
                else -> { }
            }
        })
    }

    private fun renderList(cameras: List<CameraItem>?) {
        if (cameras.isNullOrEmpty()) {
            showEmptyContent()
        } else {
            camerasAdapter.addData(cameras)
            camerasAdapter.notifyItemRangeInserted(0, cameras.size)
        }
    }

    private fun showEmptyContent() {
        binding.textView.visibility = View.INVISIBLE
        binding.textViewEmptyList.also {
            it.text =
                if (camerasViewModel.audioEnabled) getString(R.string.cameras_list_empty)
                else getString(R.string.cameras_list_audio_disabled)

            it.visibility = View.VISIBLE
        }
        binding.buttonEmptyList.also {
            it.setOnClickListener {
                camerasViewModel.logOut()

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
            it.visibility = View.VISIBLE
        }
    }

}