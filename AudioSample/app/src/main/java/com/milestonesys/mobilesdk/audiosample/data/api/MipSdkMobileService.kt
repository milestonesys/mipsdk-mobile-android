package com.milestonesys.mobilesdk.audiosample.data.api

import android.content.Context
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.CommunicationItem
import com.milestonesys.mobilesdk.audiosample.R
import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.utils.Resource
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.List

private const val AUDIO_CHANNEL_PATH = "XProtectMobile/Audio/"
private const val PARAM_AUDIO_SUPPORTED = "SupportsOutgoingAudio";

private const val ITEM_TYPE_FOLDER = "Folder"
private const val ITEM_TYPE_VIEW = "View"
private const val ITEM_TYPE_CAMERA = "Camera"
private const val ITEM_TYPE_MICROPHONE = "Microphone"

class MipSdkMobileService(context: Context, connectionUrl: URL) : ApiService {
    private val mipSdkMobile: MIPSDKMobile = MIPSDKMobile(context, connectionUrl)
    private var currentStreamId: String? = null
    private val url = connectionUrl
    private var audioEnabled = true

    private val errorConnectMessage = context.getString(R.string.error_connect)
    private val errorLoginMessage = context.getString(R.string.error_login)
    private val errorPlayMessage = context.getString(R.string.error_play_audio)
    private val errorCamerasMessage = context.getString(R.string.error_cameras)
    private val errorFeatureDisabled = context.getString(R.string.error_feature_disabled)
    private val errorInsufficientRight = context.getString(R.string.error_insufficient_rights)

    fun connect(): Resource<Boolean> {
        var errorMessage: String? = null

        mipSdkMobile.connect(
            { },
            { errorMessage = errorConnectMessage }
        )

        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    fun logIn(username: String, password: String): Resource<Boolean> {
        var errorMessage: String? = null

        mipSdkMobile.logIn(
            username,
            password,
            { audioEnabled = it.outputParam[PARAM_AUDIO_SUPPORTED] == CommunicationCommand.PARAM_AUTH_YES },
            { errorMessage = errorLoginMessage }
        )

        return if (errorMessage == null) {
            Resource.success(true)
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun getCamerasWithAudio(): Resource<List<CameraItem>> {
        val camerasSet = HashSet<CameraItem>()
        var errorMessage: String? = null

        mipSdkMobile.getAllViewsAndCameras(
            { findCamerasWithAudio(it.itemsList, camerasSet) },
            { errorMessage = errorCamerasMessage }
        )

        return if (errorMessage == null) {
            Resource.success(ArrayList(camerasSet).sortedBy { it.cameraName })
        } else {
            Resource.error(errorMessage!!, null)
        }
    }

    override fun playAudio(micId: UUID): Resource<URL> {
        stopAudio()
        var errorMessage: String? = null

        mipSdkMobile.requestAudioStream(
            micId.toString(),
            null,
            prepareDefaultAudioProps(),
            CommunicationCommand.PARAM_SIGNAL_LIVE,
            CommunicationCommand.PARAM_METHOD_PUSH,
            { currentStreamId = it.outputParam[CommunicationCommand.PARAM_VIDEO_ID]},
            { errorMessage = getErrorMessage(it.errorCode, errorPlayMessage) }
        )

        return if (errorMessage == null) {
            Resource.success(
                URL(
                    url.protocol,
                    url.host,
                    url.port,
                    AUDIO_CHANNEL_PATH + currentStreamId
                )
            )
        } else {
            currentStreamId = null
            Resource.error(errorMessage!!, null)
        }
    }

    override fun stopAudio() {
        if (currentStreamId == null) {
            return
        }
        val streamToStop = currentStreamId
        currentStreamId = null

        mipSdkMobile.stopAudioStream(
            streamToStop,
            { },
            { }
        )
    }

    override fun isAudioEnabled(): Boolean {
        return audioEnabled
    }

    private fun getErrorMessage(code: Int, default: String): String {
        return when (code) {
            CommunicationCommand.SERVERERROR_FEATURE_IS_DISABLED -> errorFeatureDisabled
            CommunicationCommand.SERVERERROR_INSUFFICIENT_RIGHTS -> errorInsufficientRight
            else -> default
        }
    }

    private fun findCamerasWithAudio(items: List<CommunicationItem>, container: HashSet<CameraItem>) {
        for (item in items) {
            when (item.type) {
                ITEM_TYPE_FOLDER, ITEM_TYPE_VIEW ->
                    findCamerasWithAudio(item.itemsList, container)

                ITEM_TYPE_CAMERA -> {
                    for (subItem in item.itemsList) {
                        if (subItem.type == ITEM_TYPE_MICROPHONE) {
                            container.add(createCameraItem(item, subItem))
                        }
                    }
                }
            }
        }
    }

    private fun prepareDefaultAudioProps(): HashMap<String, String> {
        val props = HashMap<String, String>()

        props[CommunicationCommand.PARAM_STREAM_DATA_TYPE] =
            CommunicationCommand.PARAM_AUDIO_STREAM

        props[CommunicationCommand.PARAM_AUDIO_ENCODING] =
            CommunicationCommand.PARAM_AUDIO_ENCODING_MP3

        props[CommunicationCommand.PARAM_STREAM_HEADERS] =
            CommunicationCommand.PARAM_AUDIO_WITHOUT_HEADERS

        return props
    }

    private fun createCameraItem(cameraItem: CommunicationItem, micItem: CommunicationItem): CameraItem {
        val micSupportsLiveAudio =
            micItem.getProperty(CommunicationCommand.PARAM_SIGNAL_LIVE) ==
                CommunicationCommand.PARAM_AUTH_YES

        return CameraItem(
            cameraItem.id, cameraItem.name,
            micItem.id, micItem.name, micSupportsLiveAudio
        )
    }
}