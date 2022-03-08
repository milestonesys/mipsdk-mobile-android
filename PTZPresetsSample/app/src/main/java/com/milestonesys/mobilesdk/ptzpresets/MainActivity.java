package com.milestonesys.mobilesdk.ptzpresets;

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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.milestonesys.mipsdkmobile.*;
import com.milestonesys.mipsdkmobile.callbacks.*;
import com.milestonesys.mipsdkmobile.communication.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;

/**
 * An activity that plays live video using MIPSDKMobile
 */

public class MainActivity extends AppCompatActivity implements VideoReceiver {
    public static int LIVE_EVENT_CONNECTION_LOST = 0x10;

    // The predefined id of the "all cameras" view
    private static final String ALL_CAMERAS_VIEW_ID = "bb16cc8f-a2c5-44f8-9d20-6e9ac57806f5";

    // Communication paths (appended to server address; normally shouldn't be changed)
    private static final String COMMUNICATION_ALIAS = "XProtectMobile";

    // MoS Commands
    private static final String CMD_STREAM_CHANGE = "ChangeStream";
    private static final String CMD_PTZ_PRESETS = "GetPtzPresets";

    private static final String OUTPUT_PARAM_PTZ = "PTZ";
    private static final String OUTPUT_PARAM_YES = "Yes";

    private static final String PROTOCOL = "http";

    // Connection settings (server address, user and password)
    private String address = "";
    private String user = "";
    private String password = "";
    private int port = 8081;    // The default value for HTTP

    // Keys used to store connection settings into shared preferences
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_PORT = "port";
    private static final String KEY_USER = "user";
    private static final String KEY_PASS = "pass";

    private static final String DEFAULT_WIDTH = "640";
    private static final String DEFAULT_HEIGHT = "480";

    // Connection object (MobileSDK instance)
    private MIPSDKMobile mipSdkMobile;
    private boolean connected;

    // Image used to display video
    private ImageView videoView;

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private String videoId;

    // Activity lifecycle =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.video_view);
        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Make sure to stop the video and disconnect so bandwidth is not wasted while in background
        if (connected) {
            disconnect(mipSdkMobile);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If there are no connection details, show the settings dialog
        if (address == null || address.isEmpty()) {
            showSettings();
        } else {
            // Auto-connect to the mobile server
            connectToSdk();
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
        } else if (item.getItemId() == R.id.ptz_menu_item) {
            showPTZPresets();
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
            connectionUrl = new URL(PROTOCOL, address, port, "");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            showStatus(getString(R.string.bad_address));
            return;
        }

        mipSdkMobile = new MIPSDKMobile(this, connectionUrl, COMMUNICATION_ALIAS,
                null, null, null, null, false);
    }

    private void connectToSdk() {
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
                            // Connected. Proceed with login.
                            connected = true;
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

        mipSdkMobile.logIn(user, password, new SuccessCallback() {
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
                                // Select a PTZ camera to show
                                CommunicationCommand.SubItem PTZCamera = getPtzCamera(cameras);
                                if (PTZCamera == null) {
                                    showStatus(getString(R.string.ptz_camera_not_found));
                                    return;
                                }
                                showStatus(getString(R.string.loading_camera) + " " + PTZCamera.getName());
                                requestVideo(PTZCamera.getId());
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

    private CommunicationCommand.SubItem getPtzCamera(Vector<CommunicationCommand.SubItem> cameras) {
        final CommunicationCommand.SubItem[] result = new CommunicationCommand.SubItem[1];
        final boolean[] cameraIsFound = new boolean[1];

        for (final CommunicationCommand.SubItem camera : cameras) {
            mipSdkMobile.getCameraCapabilities(camera.getId(), new SuccessCallback() {
                @Override
                public void onSuccess(CommunicationCommand communicationCommand) {
                    boolean isPtz = communicationCommand.getOutputParam().get(OUTPUT_PARAM_PTZ).equals(OUTPUT_PARAM_YES);
                    if (isPtz) {
                        result[0] = camera;
                        cameraIsFound[0] = true;
                    }
                }
            }, null);
            if (cameraIsFound[0]) {
                break;
            }
        }
        return result[0];
    }

    /**
     * Request video from the selected camera
     *
     * @param cameraId The id of the camera
     */
    private void requestVideo(String cameraId) {
        HashMap<String, String> videoProps = new HashMap<>();
        // Use a default width & height
        videoProps.put(CommunicationCommand.PARAM_WIDTH, DEFAULT_WIDTH);
        videoProps.put(CommunicationCommand.PARAM_HEIGHT, DEFAULT_HEIGHT);

        HashMap<String, Object> allProperties = new HashMap<>();
        allProperties.put(LiveVideo.CAMERA_ID_PROPERTY, cameraId);
        allProperties.put(LiveVideo.VIDEO_PROPERTIES, videoProps);

        LiveVideo liveVideo = new LiveVideo(mipSdkMobile, this, allProperties);
        liveVideo.setIsPull(true);

        ErrorState errorState = liveVideo.requestVideo();
        if (errorState != null) {
            showStatus(getString(R.string.video_error));
        }
        // Store the video ID (it is needed it for the PTZ commands)
        videoId = liveVideo.getVideoId();
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

    // PTZ Presets Handling =======================================================================

    private void showPTZPresets() {
        if (videoId == null) {
            showToastMessage(getString(R.string.preset_failed_to_get), Toast.LENGTH_SHORT);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                HashMap<String, String> props = new HashMap<>();
                props.put(CommunicationCommand.PARAM_VIDEO_ID, videoId);
                // Query the server for the PTZ presets of the given live video id
                mipSdkMobile.sendCommand(CMD_PTZ_PRESETS, props,
                        new SuccessCallback() {
                            @Override
                            public void onSuccess(CommunicationCommand response) {
                                Collection<String> presets = response.getOutputParam().values();
                                String[] arrPresets = new String[presets.size()];
                                arrPresets = presets.toArray(arrPresets);

                                // ask the user to select preset
                                onPtzPresetSelect(arrPresets);
                            }
                        },
                        new ErrorCallback() {
                            @Override
                            public void onErrorOccurred(CommunicationCommand response) {
                                showToastMessage(getString(R.string.preset_failed_to_get), Toast.LENGTH_SHORT);
                            }
                        });
            }
        }).start();
    }

    private void showToastMessage(final String toastMessage, final int lengthShort) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, toastMessage,
                        lengthShort).show();
            }
        });
    }

    // Display a list of the ptz presets
    private void onPtzPresetSelect(final String[] arrPresets) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.menu_ptz_presets)
                        .setItems(arrPresets, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                setPtzPreset(arrPresets[which]);
                            }
                        })
                        .show();
            }
        });
    }

    // Activate the selected ptz preset
    public void setPtzPreset(final String preset) {
        new Thread("Send PTZ Preset command") {
            public void run() {
                HashMap<String, String> props = new HashMap<>();
                props.put(CommunicationCommand.PARAM_VIDEO_ID, videoId);
                props.put(CommunicationCommand.PARAM_PTZ_PRESET, preset);

                mipSdkMobile.sendCommand(CMD_STREAM_CHANGE, props,
                        new SuccessCallback() {
                            @Override
                            public void onSuccess(CommunicationCommand communicationCommand) {
                                // preset activated
                                showToastMessage(getString(R.string.preset_success_activated) + " " + preset, Toast.LENGTH_SHORT);
                            }
                        },
                        new ErrorCallback() {
                            @Override
                            public void onErrorOccurred(CommunicationCommand communicationCommand) {
                                showToastMessage(getString(R.string.preset_failed_to_activate) + " " + preset, Toast.LENGTH_SHORT);
                            }
                        }
                );
            }
        }.start();
    }

    // Settings ===================================================================================

    /**
     * Display connections settings menu where server address, port, user name
     * and password are entered
     */

    private void showSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.connection_settings);
        final View view = getLayoutInflater().inflate(R.layout.connection_form, null);
        final EditText inputUrl = view.findViewById(R.id.input_url);
        final EditText inputPort = view.findViewById(R.id.input_port);
        final EditText inputUsername = view.findViewById(R.id.input_user);
        final EditText inputPassword = view.findViewById(R.id.input_password);

        inputUrl.setText(address);
        inputPort.setText(String.valueOf(port));
        inputUsername.setText(user);
        inputPassword.setText(password);

        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        address = inputUrl.getText().toString();
                        String portInputText = inputPort.getText().toString();
                        if (!portInputText.isEmpty()) {
                            port = Integer.parseInt(portInputText);
                        }
                        user = inputUsername.getText().toString();
                        password = inputPassword.getText().toString();
                        saveSettings();
                        disconnect(mipSdkMobile);
                        connectToSdk();
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
        builder.setMessage(R.string.dialog_message)
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
                ((TextView) findViewById(R.id.status_txt)).setText(msg);
                videoView.setImageResource(android.R.color.transparent);
            }
        });
    }

    /**
     * Load stored connection settings
     */
    private void loadSettings() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        address = preferences.getString(KEY_ADDRESS, address);
        port = preferences.getInt(KEY_PORT, port);
        user = preferences.getString(KEY_USER, user);
        password = preferences.getString(KEY_PASS, password);
    }

    /**
     * Store connection settings
     */
    private void saveSettings() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_ADDRESS, address);
        editor.putInt(KEY_PORT, port);
        editor.putString(KEY_USER, user);
        editor.putString(KEY_PASS, password);
        editor.apply();
    }
}
