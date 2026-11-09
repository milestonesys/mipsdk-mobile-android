package com.milestonesys.mobilesdk.bookmarkssample.utils

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class DateHelper {
    companion object {

        private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT).apply {
            isLenient = false
        }
        private val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
        private val timeFormat24 = SimpleDateFormat("HH:mm:ss", Locale.US)
        private const val DATE_TIME_SEPARATOR = " "

        fun formatted(date: Long?): String {
            return if (date == null) ""
            else dateFormat.format(date) + DATE_TIME_SEPARATOR + timeFormat.format(date)
        }

        fun formattedDateOnly(date: Long?): String {
            return if (date == null) ""
            else dateFormat.format(date)
        }

        fun formattedTimeOnly(date: Long?): String {
            return if (date == null) ""
            else timeFormat.format(date)
        }

        fun formattedTime24H(date: Long?): String {
            return if (date == null) ""
            else timeFormat24.format(date)
        }

        fun parseDateInput(input: String): Long? {
            return try {
                dateFormat.parse(input)?.time
            } catch (_: ParseException) {
                null
            }
        }
    }
}