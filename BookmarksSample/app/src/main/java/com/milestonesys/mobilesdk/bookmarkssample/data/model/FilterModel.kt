package com.milestonesys.mobilesdk.bookmarkssample.data.model

import java.util.*

private const val ANYTIME = 0
private const val LAST_2_HOURS = 1
private const val LAST_6_HOURS = 2
private const val LAST_12_HOURS = 3
private const val LAST_24_HOURS = 4
private const val TODAY = 5
private const val YESTERDAY = 6
private const val LAST_7_DAYS = 7

private const val MILLIS_IN_ONE_HOUR = 60 * 60 * 1000

data class FilterModel(
    val text: String? = null,
    val timeIntervalIndex: Int = 0,
    val camera: CameraItem? = null,
    val mineOnly: Boolean = false
) {
    var timeIntervalStart: Long? = null
        private set

    var timeIntervalEnd: Long? = null
        private set

    private val calendar = Calendar.getInstance()

    fun isNotEmpty(): Boolean {
        return text != null || timeIntervalIndex != 0 || camera != null || mineOnly
    }

    init {
        val now = System.currentTimeMillis()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val midnight = calendar.timeInMillis
        calendar.add(Calendar.DATE, -1)
        val prevMidnight = calendar.timeInMillis
        calendar.add(Calendar.DATE, 2)
        val nextMidnight = calendar.timeInMillis

        timeIntervalStart = when (timeIntervalIndex) {
            LAST_2_HOURS -> now - 2 * MILLIS_IN_ONE_HOUR
            LAST_6_HOURS -> now - 6 * MILLIS_IN_ONE_HOUR
            LAST_12_HOURS -> now - 12 * MILLIS_IN_ONE_HOUR
            LAST_24_HOURS -> now - 24 * MILLIS_IN_ONE_HOUR
            TODAY -> midnight
            YESTERDAY -> prevMidnight
            LAST_7_DAYS -> now - 7 * 24 * MILLIS_IN_ONE_HOUR
            else -> null
        }

        timeIntervalEnd = when (timeIntervalIndex) {
            LAST_2_HOURS,
            LAST_6_HOURS,
            LAST_12_HOURS,
            LAST_24_HOURS,
            LAST_7_DAYS -> now
            TODAY -> nextMidnight
            YESTERDAY -> midnight
            else -> null
        }
    }
}