package pttsample.mobile.milestonesys.com.pushtotalksample

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.milestonesys.mipsdkmobile.MIPSDKMobile
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand
import com.milestonesys.mipsdkmobile.communication.CommunicationItem
import pttsample.mobile.milestonesys.com.pushtotalksample.databinding.ActivityMainBinding
import java.net.MalformedURLException
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var applicationObject: PushToTalkSampleApplication? = null
    private var loadingDialog: Dialog? = null

    private var address: String = ""
    private var port: Int = DEFAULT_PORT
    private var username: String = ""
    private var password: String = ""

    // Keys used to store connection settings into shared preferences
    private val keyAddress = "address"
    private val keyPort = "port"
    private val keyUser = "user"
    private val keyPassword = "password"

    //Alias for server connection
    private val communicationAlias = "XProtectMobile"
    private val protocol = "http"

    companion object {
        private const val TAG = "MIPSDKMOBILE"
        private const val DEFAULT_PORT = 8081
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        EdgeToEdgeHelper.setupEdgeToEdgeTopViewInsets(toolbar)
        EdgeToEdgeHelper.setupEdgeToEdgeContentInsets(binding.scrollRoot)

        applicationObject = application as PushToTalkSampleApplication?

        loadSettings()

        initLoggingDialog()
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

    private fun initLoggingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.loading)
        loadingDialog = builder.create()

        binding.loginButton.setOnClickListener {
            if (binding.inputServerAddress.text.isNullOrEmpty() && binding.inputServerPort.text.isNullOrEmpty()) {
                return@setOnClickListener
            }

            saveSettings()
            loadingDialog?.show()

            //Creating the URL for server connection
            var connectionUrl: URL? = null
            try {
                connectionUrl = URL(
                    protocol,
                    binding.inputServerAddress.text.toString(),
                    getPortValue(),
                    ""
                )
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }

            // SDK instance is created and stored it in the application.
            applicationObject?.mipSdkMobile =
                MIPSDKMobile(this, connectionUrl, communicationAlias, null, null, null, null, false)

            //Execute connect command on a separate Thread
            Thread {
                try {
                    applicationObject?.mipSdkMobile?.connect({
                        //On successful connect login method is executed
                        logIn()
                    }, { handleError(it) })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    /**
     * Load stored connection settings
     */
    private fun loadSettings() {
        val preferences = getPreferences(Context.MODE_PRIVATE)

        preferences.getString(keyAddress, address)?.let {
            address = it
        }
        preferences.getString(keyUser, username)?.let {
            username = it
        }
        preferences.getString(keyPassword, password)?.let {
            password = it
        }
        port = preferences.getInt(keyPort, port)
    }

    /**
     * Store connection settings
     */
    private fun saveSettings() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val editor = preferences.edit()

        address = binding.inputServerAddress.text.toString()
        port = getPortValue()
        username = binding.inputUsername.text.toString()
        password = binding.inputPassword.text.toString()

        editor
            .putString(keyAddress, address)
            .putInt(keyPort, port)
            .putString(keyUser, username)
            .putString(keyPassword, password)
            .apply()
    }

    private fun getPortValue(): Int {
        return if (binding.inputServerPort.text.toString().isNotEmpty()) {
            Integer.parseInt(binding.inputServerPort.text.toString())
        } else DEFAULT_PORT
    }

    private fun assignLoginDetails() {
        binding.inputServerAddress.setText(address)
        binding.inputServerPort.setText(port.toString())
        binding.inputUsername.setText(username)
        binding.inputPassword.setText(password)
    }

    private fun showAboutDlg() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_message)
            .setTitle(R.string.app_long_name)
            .create()
            .show()
    }

    /**
     * Execute login command with username and password
     */
    private fun logIn() {
        applicationObject?.mipSdkMobile?.logIn(
            binding.inputUsername.text.toString(),
            binding.inputPassword.text.toString(),
            {
                //On success getAllViewsAndCameras method is executed
                applicationObject?.serverHost = binding.inputServerAddress.text.toString()
                applicationObject?.serverPort = getPortValue()
                getAllViewsAndCameras()
            },
            { handleError(it) }
        )
    }

    /**
     * All views and cameras command is executed
     */
    private fun getAllViewsAndCameras() {
        applicationObject?.mipSdkMobile?.getAllViewsAndCameras({

            //Clear the cameras lists
            applicationObject?.allAvailableCameras?.clear()
            applicationObject?.camerasWithSpeakers?.clear()
            getCommunicationItemByType(
                PushToTalkSampleApplication.COMMUNICATION_ITEM_TYPE_CAMERA,
                it.itemsList,
                applicationObject?.allAvailableCameras!!
            )
            val intent = Intent(this, CameraListActivity::class.java)

            //Hide loading dialog
            if (loadingDialog?.isShowing!!) {
                runOnUiThread { loadingDialog?.dismiss() }

            }

            filterCamerasWithAssociatedSpeaker(
                applicationObject?.allAvailableCameras!!,
                applicationObject?.camerasWithSpeakers!!
            )

            //Starting camera list activity
            startActivity(intent)

        }, { handleError(it) })
    }

    /**
     * Filter the cameras with associated speakers only from whole cameras list.
     */
    private fun filterCamerasWithAssociatedSpeaker(
        allCamerasList: MutableList<CommunicationItem>,
        camerasToReturn: MutableList<CommunicationItem>
    ) {
        allCamerasList.forEach { currentItem: CommunicationItem ->
            if (currentItem.type == PushToTalkSampleApplication.COMMUNICATION_ITEM_TYPE_CAMERA && hasSpeaker(
                    currentItem
                )
            ) {
                camerasToReturn.add(currentItem)
            }
        }
    }

    /**
     * Check if the passed camera has associated speaker.
     */
    private fun hasSpeaker(currentCamera: CommunicationItem): Boolean {
        currentCamera.itemsList.forEach { currentItem: CommunicationItem ->
            if (currentItem.type == PushToTalkSampleApplication.COMMUNICATION_ITEM_TYPE_SPEAKER && currentItem.id != null) {
                return true
            }
        }
        return false
    }

    /**
     * Recursive method for filtering items by their type only from response
     */
    private fun getCommunicationItemByType(
        itemToSearchFor: String,
        arrayToFilter: MutableList<CommunicationItem>,
        arrayListToReturn: MutableList<CommunicationItem>
    ) {
        //In the structure every item could be Camera, View or Folder and only the items with Camera type are get, if
        //it's not a camera but a container the method is called recursively
        arrayToFilter.forEach { currentItem: CommunicationItem ->
            if (currentItem.type != itemToSearchFor) {
                getCommunicationItemByType(
                    itemToSearchFor,
                    currentItem.itemsList,
                    arrayListToReturn
                )
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
     * Hides loading dialog and logs the error
     */
    private fun handleError(communicationCommand: CommunicationCommand?) {
        if (loadingDialog?.isShowing!!) {
            runOnUiThread { loadingDialog?.dismiss() }
        }
        Log.e(TAG, communicationCommand?.errorCode.toString())
    }
}