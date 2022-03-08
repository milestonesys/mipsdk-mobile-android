package com.milestonesys.mobilesdk.audiosample.ui.view

import android.os.Bundle
import android.view.*
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.audiosample.R

class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        enterTransition = MaterialFadeThrough()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menu.iterator().forEach { it.isVisible = false }
    }
}