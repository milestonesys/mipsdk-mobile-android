package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.databinding.FragmentLoginBinding
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel
import com.milestonesys.mobilesdk.bookmarkssample.utils.DataStatus

class LoginFragment : Fragment() {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentLoginBinding? = null

    private val bookmarksViewModel: BookmarksViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialFadeThrough()
        enterTransition = MaterialFadeThrough()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_login, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_about) {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginFragmentToAboutFragment()
            )
            return true
        }
        return super.onOptionsItemSelected(item)
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
        bookmarksViewModel.loginStatus.observe(viewLifecycleOwner, {
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
                else -> {
                }
            }
        })
    }

    private fun onLoginSuccess() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToBookmarksListFragment(),
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

        bookmarksViewModel.setup(
            binding.editTextServerAddress.text.toString(),
            Integer.parseInt(binding.editTextServerPort.text.toString()),
            binding.editTextUsername.text.toString(),
            binding.editTextPassword.text.toString(),
        )
    }

    private fun saveServerInfo() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
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