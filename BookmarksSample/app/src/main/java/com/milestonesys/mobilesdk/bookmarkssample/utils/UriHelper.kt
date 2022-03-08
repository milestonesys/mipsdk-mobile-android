package com.milestonesys.mobilesdk.bookmarkssample.utils

import java.net.MalformedURLException
import java.net.URL

class UrlHelper {

    companion object {

        private const val PROTOCOL_HTTP = "http"

        fun urlFor(address: String, port: Int): URL? {
            return try {
                URL(PROTOCOL_HTTP, address, port, "")
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                null
            }
        }
    }
}