package com.milestonesys.mobilesdk.bookmarkssample.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import java.net.URL

class SnackHelper {
    companion object {

        fun showMessage(view: View?, resourceId: Int) {
            view?.let {
                Snackbar.make(
                    it,
                    it.context.getString(resourceId),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}