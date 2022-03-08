package com.milestonesys.mobilesdk.bookmarkssample.utils

import android.icu.util.LocaleData
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.*

class DateHelper {
    companion object {

        private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT).apply {
            isLenient = false
        }
        private val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
        private val timeFormat24 = SimpleDateFormat("HH:mm:ss", Locale.US)
        private const val dateTimeSeparator = " "

        fun formatted(date: Long?): String {
            return if (date == null) ""
            else dateFormat.format(date) + dateTimeSeparator + timeFormat.format(date)
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
            } catch (e: ParseException) {
                null
            }
        }
    }
}