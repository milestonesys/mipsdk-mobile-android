package com.example.milestone.playbacksample

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object EdgeToEdgeHelper {

    private fun maxSystemInsets(insets: WindowInsetsCompat): Insets {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        return Insets.max(systemBars, cutout)
    }

    fun setupEdgeToEdgeTopViewInsets(topView: View) {
        val initialPaddingTop = topView.paddingTop
        val initialPaddingLeft = topView.paddingLeft
        val initialPaddingRight = topView.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(topView) { view, insets ->
            val bars = maxSystemInsets(insets)
            view.updatePadding(
                top = initialPaddingTop + bars.top,
                left = initialPaddingLeft + bars.left,
                right = initialPaddingRight + bars.right
            )
            insets
        }
    }

    fun setupEdgeToEdgeContentInsets(view: View, applyImeToBottom: Boolean = true) {
        val initialPaddingLeft = view.paddingLeft
        val initialPaddingRight = view.paddingRight
        val initialPaddingBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = maxSystemInsets(insets)
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (applyImeToBottom) maxOf(ime.bottom, bars.bottom) else bars.bottom
            v.updatePadding(
                left = initialPaddingLeft + bars.left,
                right = initialPaddingRight + bars.right,
                bottom = initialPaddingBottom + bottomInset
            )
            insets
        }
    }
}