package pttsample.mobile.milestonesys.com.pushtotalksample

import android.content.Context
import android.media.AudioFormat
import com.milestonesys.mipsdkmobile.communication.PushToTalkCommand
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand.SERVERERROR_INTERNAL_ERROR
import android.media.MediaRecorder
import android.media.AudioRecord
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import com.milestonesys.mipsdkmobile.communication.HTTPConnection
import com.milestonesys.mipsdkmobile.communication.ConnectionLayer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import kotlin.experimental.and

/**
 * Class that is used to catch audio waves from the device microphone and send them to the mobile server
 */
class PushToTalkEngine {
    companion object {
        const val RECORDER_SAMPLE_RATE = 8000
        const val BITS_PER_SAMPLE = "16"
        const val AUDIO_ENCODING = "Pcm"

        const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
        const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val RESPONSE_READ_TIMEOUT = 3000 //in milliseconds
        const val RESPONSE_TIMEOUT = 4000 //in milliseconds
    }

    /**
     * Audio recorder that captures the audio waves from the device
     */
    private var recorder: AudioRecord? = null

    /**
     * Flag that represents the current state of the recorder
     */
    var isRecording = false

    /**
     * Empty header bytes
     */
    private val hBuffer = ByteArray(36)

    /**
     * Audio waves buffer used to be sent to the MoS
     */
    private var waveBuffer: ByteArray? = null

    /**
     * Current audio frame counter
     */
    private var frameCount = 0

    /**
     * Used to refactor the main header of every request to the server.
     */
    private var vCmd: VideoCommand? = null

    /**
     * Id of the stream that the audio waves will be sent to.
     */
    private var streamID: String? = null

    /**
     * Communication object used for the transferring of the audio waves.
     */
    private var communication: ConnectionLayer? = null

    /**
     * Application object
     */
    private lateinit var vApp: PushToTalkSampleApplication

    /**
     * Callback that will be called when an error is received while sending frames to the MoS
     */
    private var errorsCallback: PushToTalkErrorsCallback? = null

    /**
     * Size of the buffer elements to send
     */
    private val bufferElementsToRec: Int = 2048
    private val bytesPerElement: Int = 2

    private val aliasMobile = "/XProtectMobile/Audio/"

    private val outStream = ByteArrayOutputStream()

    /**
     * Empty constructor
     */
    constructor()

    /**
     * Constructs the communication object
     *
     *@param cnt - context of the caller
     *@param streamId - id of the stream that will be used for pushing the audio frames from the client to the server
     *@param errorsCallback - callback that will be called when an error code is received for some of the sent audio frames.
     */
    constructor (cnt: Context, streamId: String, errorsCallback: PushToTalkErrorsCallback) {
        this.streamID = streamId
        vApp = cnt.applicationContext as PushToTalkSampleApplication
        this.errorsCallback = errorsCallback

        try {
            communication = HTTPConnection<HttpURLConnection>(
                vApp.serverHost,
                vApp.serverPort,
                getXProtectAlias(streamId)
            )

            (communication as HTTPConnection<*>).setMaxTimeForReadingInputStream(RESPONSE_READ_TIMEOUT)
            (communication as HTTPConnection<*>).setMaxWaitingTimeBeforeTimeout(RESPONSE_TIMEOUT)

        } catch (e: IOException) {
            e.printStackTrace()
        }

        vCmd = VideoCommand(streamId)
    }

    private fun getXProtectAlias(streamId: String): String{
        return "$aliasMobile$streamId/"
    }

    /**
     * Init of AudioRecorder and start capturing audio waves from the device's microphone
     */
    fun startRecording() {
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLE_RATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, bufferElementsToRec * bytesPerElement
        )

        recorder!!.startRecording()
        isRecording = true
        Thread(Runnable { catchAudioData() }).start()
    }

    /**
     * Convert the given ShortArray to a ByteArray
     */
    private fun shortToByte(sData: ShortArray): ByteArray {
        val shortArrSize = sData.size
        val bytes = ByteArray(shortArrSize * 2)

        for (i in 0 until shortArrSize) {
            bytes[i * 2] = (sData[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (sData[i].toInt() shr 8).toByte()
            sData[i] = 0
        }

        return bytes
    }

    /**
     * Catch the audio waves from the microphone and send them to the MoS
     */
    private fun catchAudioData() {
        val sData = ShortArray(bufferElementsToRec)
        try {
            while (isRecording) {
                recorder!!.read(sData, 0, bufferElementsToRec)
                val bData = shortToByte(sData)
                outStream.write(bData, 0, bufferElementsToRec * bytesPerElement)
                val dataToSend = outStream.toByteArray().clone()
                outStream.reset()
                sendData(dataToSend)
            }
        } catch (e: OutOfMemoryError) {
            stopRecording()
        }
    }

    /**
     * Sends the audio frame as byte array and parses the result. If there is an error, then the errorCallback will be called with the received error code.
     */
    @Synchronized
    private fun sendData(data: ByteArray?) {
        if (data == null) return

        val bOut = ByteArrayOutputStream()
        try {
            bOut.write(hBuffer)
            bOut.write(data)

            waveBuffer = bOut.toByteArray()
            frameCount++
            if (vCmd != null) {
                vCmd!!.refactorMainHeader(waveBuffer, frameCount, System.currentTimeMillis())
            } else {
                stopRecording()
                return
            }
            if (communication != null) {
                if (communication?.sendByteArrayRequest(waveBuffer) != 0) {
                    errorsCallback?.onErrorOccurred(SERVERERROR_INTERNAL_ERROR)
                    stopRecording()
                } else {
                    val responseResult = getResponseResult(communication?.receiveResponse())
                    if (responseResult != 0) {
                        errorsCallback?.onErrorOccurred(responseResult)
                        stopRecording()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                bOut.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Parses the given input stream and checks if there are headers with data.
     * @return result of the response - 0 for OK.
     */
    private fun getResponseResult(inputStream: InputStream?): Int {
        var result = 0
        if (inputStream != null) {
            try {
                val headersBuffer = ByteArray(40)
                var bytesRead = inputStream.read(headersBuffer, 0, headersBuffer.size)

                if (bytesRead != -1) {
                    val pttCommand = PushToTalkCommand(streamID, headersBuffer)
                    val headerFlags = pttCommand.extHeaderFlags

                    if (headerFlags.toInt() != 0) {
                        //read whole header
                        val bufExtHeader =
                            ByteArray(pttCommand.extHeaderBytesSize - headersBuffer.size)
                        bytesRead = 0
                        while (bytesRead != bufExtHeader.size) {
                            bytesRead += inputStream.read(
                                bufExtHeader,
                                bytesRead,
                                bufExtHeader.size - bytesRead
                            )
                            if (bytesRead == -1) break
                        }

                        if ((PushToTalkCommand.HEADER_EXTENTION_DYNAMIC_INFO and headerFlags.toInt()) == PushToTalkCommand.HEADER_EXTENTION_DYNAMIC_INFO) {
                            pttCommand.setHeaderDynamicInfo(bufExtHeader, 0)
                            result = pttCommand.headerDeviceStateInfo.errorCode
                        }
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return result
    }

    /**
     * Stops the audio waves caught from the microphone
     */
    @Synchronized
    fun stopRecording() {
        // stops the recording activity
        try {
            if (recorder != null) {
                isRecording = false
                recorder!!.stop()
                recorder!!.release()
                recorder = null
                Thread(Runnable {
                    if (streamID != null) {
                        vApp.mipSdkMobile?.stopAudioStream(streamID, null, null)
                    }
                }).start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            communication?.closeConnection()
            communication = null
        }
    }
}