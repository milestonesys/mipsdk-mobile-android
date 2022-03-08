package pttsample.mobile.milestonesys.com.pushtotalksample

/**
 * PTT interface with an onErrorOccurred method used to be called when an error is occurred while sending audio frames to the Mobile server
 */
interface PushToTalkErrorsCallback{
    fun onErrorOccurred(errorCode: Int)
}