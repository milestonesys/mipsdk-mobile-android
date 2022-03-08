package com.milestonesys.mobilesdk.livevideosample;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.milestonesys.mipsdkmobile.*;
import com.milestonesys.mipsdkmobile.callbacks.*;
import com.milestonesys.mipsdkmobile.communication.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

/**
 * An activity that plays live video using MIPSDKMobile
 */

public class MainActivity extends AppCompatActivity implements VideoReceiver {
    public static int LIVE_EVENT_CONNECTION_LOST = 0x10;

    // The predefined id of the "all cameras" view
    private static final String ALL_CAMERAS_VIEW_ID = "bb16cc8f-a2c5-44f8-9d20-6e9ac57806f5";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String DEFAULT_WIDTH = "640";
    private static final String DEFAULT_HEIGHT = "480";

    // Keys used to store connection settings into shared preferences
    private static final String ADDRESS_KEY = "address";
    private static final String PORT_KEY = "port";
    private static final String USER_KEY = "user";
    private static final String PASS_KEY = "pass";
    private static final String SECURE_KEY = "secure";

    // Connection settings 
    private String address = "";
    private String user = "";
    private String pass = "";
    private int port = 8081;    // The default value for HTTP
    private boolean secure;
    private boolean connected;

    // Connection object (MobileSDK instance)
    private MIPSDKMobile mipSdkMobile;

    // Image used to display video
    private ImageView videoView;
    private TextView statusView;

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    // Activity lifecycle =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        statusView = findViewById(R.id.status_txt);
        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Make sure to stop video and disconnect so to not waste bandwidth while in background
        if (connected) {
            disconnect(mipSdkMobile);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If connection details are missing, show the settings dialog
        if (address == null || address.isEmpty()) {
            showSettings();
        } else {
            // Auto-connect to the mobile server
            connect();
        }
    }

    // Options menu handling ======================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.settings_menu_item) {
            showSettings();
        } else if (item.getItemId() == R.id.about_menu_item) {
            showAboutDlg();
        } else {
            return false;
        }

        return true;
    }

    // Connection Management ======================================================================

    private void initSdk() {
        URL connectionUrl;

        try {
            connectionUrl = new URL(secure ? HTTPS : HTTP, address, port, "");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            showStatus(getString(R.string.wrong_address));
            return;
        }

        // Create global connection object.
        mipSdkMobile = new MIPSDKMobile(this, connectionUrl);
    }

    private void connect() {
        initSdk();
        // Start the connection sequence on a separate thread so not to block the UI
        showStatus(getString(R.string.connecting));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mipSdkMobile.connect(new SuccessCallback() {
                        @Override
                        public void onSuccess(CommunicationCommand response) {
                            connected = true;
                            // Connected. Proceed with login.
                            logIn();
                        }
                    }, new ErrorCallback() {
                        @Override
                        public void onErrorOccurred(CommunicationCommand response) {
                            Log.d(LOG_TAG, "Error connecting: " + (response == null ? "?" : response.getError()));
                            connected = false;
                            showStatus(getString(R.string.connect_fail));
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    showStatus(getString(R.string.connect_fail));
                }
            }
        }).start();
    }


    /**
     * Log in to the connected server (use stored user credentials)
     */
    private void logIn() {
        showStatus(getString(R.string.logging_in));

        mipSdkMobile.logIn(user, pass, new SuccessCallback() {
            @Override
            public void onSuccess(CommunicationCommand response) {
                showStatus(getString(R.string.loading_cameras));

                // Get All Cameras view (the view that holds all the cameras in the system)
                mipSdkMobile.getViews(ALL_CAMERAS_VIEW_ID,
                        new SuccessCallback() {
                            @Override
                            public void onSuccess(CommunicationCommand response) {
                                Vector<CommunicationCommand.SubItem> cameras = response.getSubItems();
                                Log.d(LOG_TAG, "Received All Cameras view with " + cameras.size() + " cameras");
                                // Select a random camera to show
                                Random r = new Random();
                                int cameraNumber = r.nextInt(cameras.size());
                                CommunicationCommand.SubItem camera = cameras.get(cameraNumber);
                                showStatus(getString(R.string.loading_camera) + " " + camera.getName());
                                requestVideo(camera.getId());
                            }
                        },
                        new ErrorCallback() {
                            @Override
                            public void onErrorOccurred(CommunicationCommand response) {
                                Log.d(LOG_TAG, "Error getting cameras: " + (response == null ? "?" : response.getError()));
                                showStatus(getString(R.string.error_getting_cameras));
                            }
                        }
                );
            }
        }, new ErrorCallback() {
            @Override
            public void onErrorOccurred(CommunicationCommand response) {
                Log.d(LOG_TAG, "Error Login: " + (response == null ? "?" : response.getError()));
                showStatus(getString(R.string.login_fail));
            }
        });
    }

    /**
     * Request video from the selected camera
     *
     * @param cameraId The id of the camera
     */
    private void requestVideo(String cameraId) {
        HashMap<String, String> videoProps = new HashMap<String, String>();

        videoProps.put(CommunicationCommand.PARAM_WIDTH, DEFAULT_WIDTH);
        videoProps.put(CommunicationCommand.PARAM_HEIGHT, DEFAULT_HEIGHT);

        HashMap<String, Object> allProperties = new HashMap<>();
        allProperties.put(LiveVideo.CAMERA_ID_PROPERTY, cameraId);
        allProperties.put(LiveVideo.VIDEO_PROPERTIES, videoProps);
        LiveVideo liveVideo = new LiveVideo(mipSdkMobile, this, allProperties);
        liveVideo.setIsPull(true);
        ErrorState errorResult = liveVideo.requestVideo();
        if (errorResult != null) {
            showStatus(getString(R.string.video_error));
        }
    }


    /**
     * Called on each received video frame
     *
     * @param videoCommand the server response containing the frame (headers + image)
     */
    @Override
    public void receiveVideo(final VideoCommand videoCommand) {
        // Check for connection lost
        if (connected && videoCommand.headerLiveEvents != null && (videoCommand.headerLiveEvents.getCurrentFlags() & LIVE_EVENT_CONNECTION_LOST) != 0) {
            showStatus(getString(R.string.msg_connection_lost));
            return;
        }

        // The image data are contained in the payload
        if (videoCommand.getPayloadSize() > 0) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(videoCommand.getPayload(), 0, videoCommand.getPayloadSize());

            final Bitmap bmp = BitmapFactory.decodeStream(inputStream);
            try {
                if (bmp != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            videoView.setImageBitmap(bmp);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(LOG_TAG, "Error decoding image");
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Disconnect from the server.
     */
    private void disconnect(final MIPSDKMobile sdk) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (sdk != null) {
                    sdk.closeCommunication();
                    connected = false;
                    showStatus(getString(R.string.status_disconnected));
                }
            }
        }).start();
    }

    // Settings ===================================================================================

    /**
     * Show connections settings menu
     */

    private void showSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.connection_settings);
        final View view = getLayoutInflater().inflate(R.layout.connection_form, null);
        final EditText urlInput = view.findViewById(R.id.txt_url);
        final EditText portInput = view.findViewById(R.id.txt_port);
        final EditText userInput = view.findViewById(R.id.txt_user);
        final EditText passwordInput = view.findViewById(R.id.txt_password);
        final CheckBox checkSecureConnection = view.findViewById(R.id.check_secure_connection);

        urlInput.setText(address);
        portInput.setText(String.valueOf(this.port));
        userInput.setText(user);
        passwordInput.setText(pass);
        checkSecureConnection.setChecked(secure);

        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        address = urlInput.getText().toString();
                        String portInputText = portInput.getText().toString();
                        if (!portInputText.isEmpty()) {
                            port = Integer.parseInt(portInputText);
                        }
                        user = userInput.getText().toString();
                        pass = passwordInput.getText().toString();
                        secure = checkSecureConnection.isChecked();
                        saveSettings();
                        disconnect(mipSdkMobile);
                        connect();
                    }
                }
        );
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Display application name and version number
     */
    private void showAboutDlg() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dlg_about_msg)
                .setTitle(R.string.app_long_name);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Status message overlay. Can be called from any thread.
     *
     * @param msg Message text to display
     */
    private void showStatus(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(msg);
                videoView.setImageResource(android.R.color.transparent);
            }
        });
    }

    /**
     * Load stored connection settings
     */
    private void loadSettings() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        address = preferences.getString(ADDRESS_KEY, address);
        port = preferences.getInt(PORT_KEY, port);
        user = preferences.getString(USER_KEY, user);
        pass = preferences.getString(PASS_KEY, pass);
        secure = preferences.getBoolean(SECURE_KEY, secure);
    }

    /**
     * Store connection settings
     */
    private void saveSettings() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(ADDRESS_KEY, address);
        editor.putInt(PORT_KEY, port);
        editor.putString(USER_KEY, user);
        editor.putString(PASS_KEY, pass);
        editor.putBoolean(SECURE_KEY, secure);
        editor.apply();
    }
}
