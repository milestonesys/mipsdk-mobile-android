package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.milestonesys.mobilesdk.bookmarkssample.R

class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialFadeThrough()
    }
}