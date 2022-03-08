package com.milestonesys.mobilesdk.audiosample.utils

data class Resource<out T>(val status: DataStatus, val data: T?, val message: String?) {
    companion object {

        fun <T> success(data: T?): Resource<T> {
            return Resource(DataStatus.SUCCESS, data, null)
        }

        fun <T> error(msg: String, data: T?): Resource<T> {
            return Resource(DataStatus.ERROR, data, msg)
        }

        fun <T> loading(data: T?): Resource<T> {
            return Resource(DataStatus.LOADING, data, null)
        }

        fun <T> unset(data: T?): Resource<T> {
            return Resource(DataStatus.UNSET, data, null)
        }
    }
}