package com.milestonesys.mobilesdk.directstreamingsample

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.callbacks.VideoStreamCallback
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand.SubItem
import com.milestonesys.mipsdkmobile.communication.VideoChannelThread
import com.milestonesys.mipsdkmobile.communication.VideoCommand
import com.milestonesys.mobilesdk.directstreamingsample.DirectStreamingPlayer.Companion.IPlayerEventListener
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.*

/**
 * An activity that plays live video using MIPSDKMobile
 */
class MainActivity : AppCompatActivity(), IPlayerEventListener {
    // Connection settings 
    private var address: String? = ""
    private var user: String? = ""
    private var pass: String? = ""
    private var port = 8081 // The default value for HTTP
    private var secure = false
    private var connected = false

    // Connection object (MobileSDK instance)
    private var mipSdkMobile: MIPSDKMobile? = null

    // View used to display video
    private var directStreamingPlayer: DirectStreamingPlayer? = null
    private var statusView: TextView? = null
    private var cameraNumber = -1
    private var currentVideoChannel: VideoChannelThread? = null
    private var hasFallback: Boolean = false

    // Activity lifecycle =========================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.status_txt)
        loadSettings()
    }

    override fun onPause() {
        super.onPause()
        // Make sure to stop video and disconnect so to not waste bandwidth while in background
        if (connected) {
            disconnect(mipSdkMobile)
        }
    }

    override fun onResume() {
        super.onResume()
        // If connection details are missing, show the settings dialog
        if (address == null || address!!.isEmpty()) {
            showSettings()
        } else {
            // Auto-connect to the mobile server
            connect()
        }
    }

    // Options menu handling ======================================================================
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings_menu_item -> {
                showSettings()
            }
            R.id.about_menu_item -> {
                showAboutDlg()
            }
            R.id.cameras_menu_item -> {
                cameraNumber = -1
                if (connected) {
                    allCameraViews
                } else {
                    connect()
                }
            }
            else -> {
                return false
            }
        }
        return true
    }

    // Connection Management ======================================================================
    private fun initSdk() {
        val connectionUrl: URL = try {
            URL(if (secure) HTTPS else HTTP, address, port, "")
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            showStatus(getString(R.string.wrong_address))
            return
        }

        // Create global connection object.
        mipSdkMobile = MIPSDKMobile(this, connectionUrl)
    }

    private fun connect() {
        initSdk()
        // Start the connection sequence on a separate thread so not to block the UI
        showStatus(getString(R.string.connecting))
        Thread {
            try {
                mipSdkMobile!!.connect({
                    connected = true
                    // Connected. Proceed with login.
                    logIn()
                }) { response: CommunicationCommand? ->
                    Log.d(LOG_TAG, "Error connecting: " + if (response == null) "?" else response.error)
                    connected = false
                    showStatus(getString(R.string.connect_fail))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showStatus(getString(R.string.connect_fail))
            }
        }.start()
    }

    /**
     * Log in to the connected server (use stored user credentials)
     */
    private fun logIn() {
        showStatus(getString(R.string.logging_in))
        mipSdkMobile!!.logIn(user, pass, {
            showStatus(getString(R.string.loading_cameras))
            allCameraViews
        }) { errorResponse: CommunicationCommand? ->
            Log.d(LOG_TAG, "Error Login: " + if (errorResponse == null) "?" else errorResponse.error)
            showStatus(getString(R.string.login_fail))
        }
    }

    /**
     * Get All Cameras view (the view that holds all the cameras in the system)
     */
    private val allCameraViews: Unit
        get() {
            // Get All Cameras view (the view that holds all the cameras in the system)
            mipSdkMobile!!.getViews(ALL_CAMERAS_VIEW_ID,
                    { allCamerasResponse: CommunicationCommand ->
                        if (cameraNumber >= 0) {
                            connectToCamera(allCamerasResponse.subItems[cameraNumber])
                        } else {
                            runOnUiThread { showCameraListDialog(allCamerasResponse.subItems) }
                        }
                    }
            ) { errorResponse: CommunicationCommand? ->
                Log.d(LOG_TAG, "Error getting cameras: " + if (errorResponse == null) "?" else errorResponse.error)
                showStatus(getString(R.string.error_getting_cameras))
            }
        }

    private fun connectToCamera(camera: SubItem) {
        showStatus(getString(R.string.loading_camera) + " " + camera.name)
        val videoProps = HashMap<String, String>()
        videoProps[CommunicationCommand.PARAM_WIDTH] = DEFAULT_WIDTH
        videoProps[CommunicationCommand.PARAM_HEIGHT] = DEFAULT_HEIGHT
        runOnUiThread {
            directStreamingPlayer = DirectStreamingPlayer(findViewById(R.id.videoPlayer), this@MainActivity, this@MainActivity)
            directStreamingPlayer!!.getPlayerView().visibility = View.VISIBLE
        }
        Thread {
            mipSdkMobile!!.requestDirectStreaming(camera.id, videoProps,
                    { communicationCommand: CommunicationCommand? -> requestVideoSuccessHandling(communicationCommand) }
            ) { }
        }.start()
    }

    private fun requestVideoSuccessHandling(response: CommunicationCommand?) {
        if (response != null) {
            hasFallback = false
            val oParams = response.outputParam
            val videoID = oParams[CommunicationCommand.PARAM_VIDEO_ID]
            val streamType = oParams[CommunicationCommand.PARAM_STREAM_TYPE]
            if (CommunicationCommand.RESULT_ERROR != response.result) {
                try {
                    createVideoChannel(videoID, address, port, if (secure) HTTPS else HTTP, streamType)
                } catch (e: IOException) {
                }
            }
        }
    }

    private fun stopVideo() {
        if (currentVideoChannel == null) return
        if (mipSdkMobile != null) {
            mipSdkMobile!!.stopVideoStream(currentVideoChannel!!.videoId, null, null)
            currentVideoChannel!!.stopThread()
            currentVideoChannel = null
        }
        directStreamingPlayer!!.release()
    }

    @Throws(IOException::class)
    private fun createVideoChannel(videoID: String?, host: String?, port: Int, protocol: String, streamType: String?) {
        currentVideoChannel = VideoChannelThread(videoID, host, port, SERVER_ALIAS, protocol, false, streamType, DEFAULT_FPS, false, object : VideoStreamCallback {
            override fun receivedFrame(videoIdS: String, currentFrame: VideoCommand) {
            }

            override fun stopVideoStream(videoIdS: String) {
                if (!hasFallback) {
                    showStatus(getString(R.string.msg_connection_lost))

                }
            }
        }
        )

        runOnUiThread { directStreamingPlayer!!.getPlayerView().visibility = View.VISIBLE }
        currentVideoChannel?.setVideoReceiver(directStreamingPlayer)
        currentVideoChannel?.start()
    }

    /**
     * Disconnect from the server.
     */
    private fun disconnect(sdk: MIPSDKMobile?) {
        Thread {
            if (sdk != null) {
                sdk.closeCommunication()
                connected = false
                showStatus(getString(R.string.status_disconnected))
            }
        }.start()
    }
    // Settings ===================================================================================
    /**
     * Show connections settings menu
     */
    private fun showSettings() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.connection_settings)
        val view = layoutInflater.inflate(R.layout.connection_form, null)
        val urlInput = view.findViewById<EditText>(R.id.txt_url)
        val portInput = view.findViewById<EditText>(R.id.txt_port)
        val userInput = view.findViewById<EditText>(R.id.txt_user)
        val passwordInput = view.findViewById<EditText>(R.id.txt_password)
        val checkSecureConnection = view.findViewById<CheckBox>(R.id.check_secure_connection)
        urlInput.setText(address)
        portInput.setText(port.toString())
        userInput.setText(user)
        passwordInput.setText(pass)
        checkSecureConnection.isChecked = secure
        builder.setView(view)
        builder.setPositiveButton(R.string.ok
        ) { _: DialogInterface?, _: Int ->
            address = urlInput.text.toString()
            val portInputText = portInput.text.toString()
            if (portInputText.isNotEmpty()) {
                port = portInputText.toInt()
            }
            user = userInput.text.toString()
            pass = passwordInput.text.toString()
            secure = checkSecureConnection.isChecked
            saveSettings()
            disconnect(mipSdkMobile)
            connect()
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Show retrieved cameras
     */
    private fun showCameraListDialog(cameras: Vector<SubItem>) {
        val cameraArray = arrayOfNulls<String>(cameras.size)
        val it: Iterator<SubItem> = cameras.iterator()
        var position = 0
        while (it.hasNext()) {
            cameraArray[position++] = it.next().name
        }
        val camerasDialog = AlertDialog.Builder(this@MainActivity)
        camerasDialog.setTitle(R.string.select_camera)
        val arrayAdapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_1)
        arrayAdapter.addAll(*cameraArray)
        camerasDialog.setAdapter(arrayAdapter) { dialog: DialogInterface, which: Int ->
            stopVideo()
            cameraNumber = which
            connectToCamera(cameras[which])
            dialog.dismiss()
        }
        camerasDialog.show()
    }

    /**
     * Display application name and version number
     */
    private fun showAboutDlg() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_dlg_about_msg)
                .setTitle(R.string.app_long_name)
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Status message overlay. Can be called from any thread.
     *
     * @param msg Message text to display
     */
    private fun showStatus(msg: String) {
        runOnUiThread {
            statusView!!.text = msg
            if (directStreamingPlayer != null) {
                directStreamingPlayer!!.getPlayerView().visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Load stored connection settings
     */
    private fun loadSettings() {
        val preferences = getPreferences(MODE_PRIVATE)
        address = preferences.getString(ADDRESS_KEY, address)
        port = preferences.getInt(PORT_KEY, port)
        user = preferences.getString(USER_KEY, user)
        pass = preferences.getString(PASS_KEY, pass)
        secure = preferences.getBoolean(SECURE_KEY, secure)
    }

    /**
     * Store connection settings
     */
    private fun saveSettings() {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString(ADDRESS_KEY, address)
        editor.putInt(PORT_KEY, port)
        editor.putString(USER_KEY, user)
        editor.putString(PASS_KEY, pass)
        editor.putBoolean(SECURE_KEY, secure)
        editor.apply()
    }

    /**
     * Fallback from Direct streaming
     */
    override fun fallBack() {
        hasFallback = true
        stopVideo()
        showStatus(getString(R.string.error_support_direct_streaming))
    }

    override fun connectionLost() {
        showStatus(getString(R.string.msg_camera_connection_lost))
    }

    companion object {
        // The predefined id of the "all cameras" view
        private const val ALL_CAMERAS_VIEW_ID = "bb16cc8f-a2c5-44f8-9d20-6e9ac57806f5"
        private const val HTTP = "http"
        private const val HTTPS = "https"
        private const val DEFAULT_WIDTH = "640"
        private const val DEFAULT_HEIGHT = "480"

        // Keys used to store connection settings into shared preferences
        private const val ADDRESS_KEY = "address"
        private const val PORT_KEY = "port"
        private const val USER_KEY = "user"
        private const val PASS_KEY = "pass"
        private const val SECURE_KEY = "secure"
        private const val SERVER_ALIAS = "XProtectMobile"
        private const val DEFAULT_FPS = 8f
        private val LOG_TAG = MainActivity::class.java.simpleName

        // Video header flags
        const val LIVE_EVENT_CONNECTION_LOST = 0x10
    }
}