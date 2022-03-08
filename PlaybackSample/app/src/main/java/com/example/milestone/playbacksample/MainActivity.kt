package com.example.milestone.playbacksample

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import java.net.URL
import com.milestonesys.mipsdkmobile.communication.CommunicationItem
import androidx.appcompat.app.AlertDialog
import android.view.Menu
import java.net.MalformedURLException
import android.view.MenuItem
import android.view.View

/**
 * An activity that connects and gets all the available cameras from server using MIPSDKMobile
 */
class MainActivity : AppCompatActivity() {

    private var serverAddressField: EditText? = null
    private var serverPortField: EditText? = null
    private var loginButton: Button? = null
    private var usernameField: EditText? = null
    private var passwordField: EditText? = null
    private var applicationObject: SDKSampleApplication? = null
    private var loadingDialog: Dialog? = null

    private var address: String = ""
    private var port: Int = 8081
    private var user: String = ""
    private var pass: String = ""

    // Keys used to store connection settings into shared preferences
    private val keyAddress = "address"
    private val keyPort = "port"
    private val keyUser = "user"
    private val keyPass = "pass"

    //Alias for server connection
    private val communicationAlias = "XProtectMobile"
    private val protocol = "http"
    private val communicationItemCamera = "Camera"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applicationObject = application as SDKSampleApplication?

        setupViews()
        loadSettings()
    }

    private fun setupViews() {
        serverAddressField = findViewById(R.id.server_address)
        serverPortField = findViewById(R.id.server_port)
        loginButton = findViewById(R.id.login_button)
        usernameField = findViewById(R.id.username)
        passwordField = findViewById(R.id.password)

        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.loading)
        loadingDialog = builder.create()

        loginButton?.setOnClickListener(loginButtonListener)
    }

    private val loginButtonListener = View.OnClickListener {
        saveSettings()
        loadingDialog?.show()

        initSdk()

        //Execute connect command on a separate Thread
        Thread(Runnable {
            connectToSdk()
        }).start()
    }

    private fun connectToSdk() {
        try {
            applicationObject?.mipSdkMobile?.connect({
                //On successful connect login method is executed
                logIn()
            }, {
                handleError(it)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        assignLoginDetails()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about_menu_item -> showAboutDlg()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initSdk() {
        // Creating the URL for server connection
        var connectionUrl: URL? = null
        try {
            connectionUrl = URL(protocol, serverAddressField?.text.toString(), port, "")
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }
        applicationObject?.mipSdkMobile = MIPSDKMobile(this, connectionUrl, communicationAlias,
                null, null, null, null, false)
    }

    /**
     * Load stored connection settings
     */
    private fun loadSettings() {
        val preferences = getPreferences(Context.MODE_PRIVATE)

        preferences.getString(keyAddress, address)?.let {
            address = it
        }
        preferences.getString(keyUser, user)?.let {
            user = it
        }
        preferences.getString(keyPass, pass)?.let {
            pass = it
        }
        port = preferences.getInt(keyPort, port)
    }

    /**
     * Store connection settings
     */
    private fun saveSettings() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val editor = preferences.edit()

        address = serverAddressField?.text.toString()
        val portInputText = serverPortField?.text.toString()
        if (!portInputText.isNullOrEmpty()) {
            port = Integer.parseInt(portInputText)
        }
        serverPortField?.setText(port.toString())
        user = usernameField?.text.toString()
        pass = passwordField?.text.toString()

        editor.putString(keyAddress, address)
                .putInt(keyPort, port)
                .putString(keyUser, user)
                .putString(keyPass, pass)
                .apply()
    }

    private fun assignLoginDetails() {
        serverAddressField?.setText(address)
        serverPortField?.setText(port.toString())
        usernameField?.setText(user)
        passwordField?.setText(pass)
    }

    private fun showAboutDlg() {
        AlertDialog.Builder(this).setMessage(R.string.dlg_about_message)
                .setTitle(R.string.app_long_name)
                .create()
                .show()
    }

    /**
     * Execute login command with username and password
     */
    private fun logIn() {
        applicationObject?.mipSdkMobile?.logIn(usernameField?.text.toString(), passwordField?.text.toString(), {
            //On success getAllViewsAndCameras method is executed
            getAllViewsAndCameras()
        }, { handleError(it) })
    }

    /**
     * All views and cameras command is executed
     */
    private fun getAllViewsAndCameras() {
        applicationObject?.mipSdkMobile?.getAllViewsAndCameras({
            //Filter the result and store it
            getCamerasOnly(it.itemsList, applicationObject?.allAvailableCameras!!)
            val intent = Intent(this, CameraListActivity::class.java)

            dismissLoadingDialog()
            //Start camera list activity
            startActivity(intent)

        }, { handleError(it) })
    }

    private fun dismissLoadingDialog() {
        if (loadingDialog?.isShowing!!) {
            runOnUiThread { loadingDialog?.dismiss() }
        }
    }

    /**
     * Recursive method for filtering cameras only from response
     */
    private fun getCamerasOnly(arrayToFilter: MutableList<CommunicationItem>, arrayListToReturn: MutableList<CommunicationItem>) {
        //In the structure every item could be Camera, View or Folder and only the items with Camera type are get, if
        //it's not a camera but a container the method is called recursively
        arrayToFilter.forEach { currentItem: CommunicationItem ->
            if (currentItem.type != communicationItemCamera) {
                getCamerasOnly(currentItem.itemsList, arrayListToReturn)
            } else {
                var toAdd = true
                arrayListToReturn.forEach { currentIteratedItem: CommunicationItem ->
                    if (currentIteratedItem.id == currentItem.id) {
                        toAdd = false
                    }
                }
                if (toAdd) {
                    arrayListToReturn.add(currentItem)
                }
            }
        }
    }

    /**
     * Hides loading dialog and Logs the error
     */
    private fun handleError(communicationCommand: CommunicationCommand?) {
        dismissLoadingDialog()
        Log.e("MIPSDKMobile", communicationCommand?.errorCode.toString())
    }
}

