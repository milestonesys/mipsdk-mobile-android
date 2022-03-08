package com.milestonesys.mobilesdk.audiosample.ui.view

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.milestonesys.mobilesdk.audiosample.ui.viewmodel.CamerasViewModel
import com.milestonesys.mobilesdk.audiosample.utils.DataStatus

import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.audiosample.R
import com.milestonesys.mobilesdk.audiosample.databinding.FragmentLoginBinding


class LoginFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentLoginBinding? = null

    private val camerasViewModel: CamerasViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialFadeThrough()
        enterTransition = MaterialFadeThrough()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menu.findItem(R.id.menu_item_logout).isVisible = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObserver()
    }

    private fun setupUI() {

        loadServerInfo()

        binding.buttonLogin.setOnClickListener {
            login()
        }
    }

    private fun setupObserver() {
        camerasViewModel.loginStatus.observe(viewLifecycleOwner, {
            when (it.status) {
                DataStatus.LOADING -> {
                    onLoadingStarted()
                }
                DataStatus.SUCCESS -> {
                    onLoadingComplete()
                    onLoginSuccess()
                }
                DataStatus.ERROR -> {
                    onLoadingComplete()
                    onLoginFailure(it.message)
                }
                else -> { }
            }
        })
    }

    private fun onLoginSuccess() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToCamerasListFragment(),
        )
    }

    private fun onLoginFailure(message: String?) {
        Snackbar.make(
            binding.root,
            message ?: getString(R.string.error_network),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun login() {
        saveServerInfo()

        camerasViewModel.setup(
            binding.editTextServerAddress.text.toString(),
            Integer.parseInt(binding.editTextServerPort.text.toString()),
            binding.editTextUsername.text.toString(),
            binding.editTextPassword.text.toString(),
        )
    }

    private fun saveServerInfo() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(
                getString(R.string.preferences_address),
                binding.editTextServerAddress.text.toString()
            )
            putString(
                getString(R.string.preferences_port),
                binding.editTextServerPort.text.toString()
            )
            putString(
                getString(R.string.preferences_user),
                binding.editTextUsername.text.toString()
            )
            putString(
                getString(R.string.preferences_pass),
                binding.editTextPassword.text.toString()
            )
            apply()
        }
    }

    private fun loadServerInfo() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return

        sharedPref.getString(getString(R.string.preferences_address), null)?.let {
            binding.editTextServerAddress.setText(it)
        }
        sharedPref.getString(getString(R.string.preferences_port), null)?.let {
            binding.editTextServerPort.setText(it)
        }
        sharedPref.getString(getString(R.string.preferences_user), null)?.let {
            binding.editTextUsername.setText(it)
        }
        sharedPref.getString(getString(R.string.preferences_pass), null)?.let {
            binding.editTextPassword.setText(it)
        }
    }

    private fun onLoadingStarted() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.mainLayout.children.forEach { it.isEnabled = false }
    }

    private fun onLoadingComplete() {
        binding.progressIndicator.visibility = View.GONE
        binding.mainLayout.children.forEach { it.isEnabled = true }
    }
}