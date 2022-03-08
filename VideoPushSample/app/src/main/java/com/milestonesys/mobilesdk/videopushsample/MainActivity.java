package com.milestonesys.mobilesdk.videopushsample;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.milestonesys.mipsdkmobile.MIPSDKMobile;

import java.net.MalformedURLException;
import java.net.URL;

import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.COMMUNICATION_ALIAS;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.COMMUNICATION_PROTOCOL;

public class MainActivity extends AppCompatActivity {
    
    private static final int REQUEST_CODE_CAMERA = 101;
    public static final int REQUEST_PERMISSIONS_DENIED = 201;

    // Connection settings (server address, user and password)
    private String address = "";
    private String userName = "";
    private String password = "";
    private int port = 8081;    // The default value for HTTP

    // Keys used to store connection settings into shared preferences
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "user";
    private static final String KEY_PASS = "pass";

    // Connection object (MobileSDK application instance)
    public VideoPushApplication app;

    private TextView statusView = null;
    private Button loginButton, connectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupViews();
        loadSettings();
    }

    private void setupViews() {
        app = (VideoPushApplication) getApplication();

        statusView = findViewById(R.id.status_txt);
        loginButton = findViewById(R.id.button_login);
        connectButton = findViewById(R.id.button_reconnect);

        loginButton.setOnClickListener(v -> logIn());
        connectButton.setOnClickListener(v -> connect());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If there are no connection details, show the settings dialog
        if (address == null || address.isEmpty()) {
            showSettings();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CAMERA) {
            if (resultCode == REQUEST_PERMISSIONS_DENIED){
                Toast.makeText(this, getString(R.string.video_push_permissions_needed,
                        getString(R.string.app_name)), Toast.LENGTH_SHORT).show();
            }
            disconnect(app.mipSdkMobile);
            showLoginButton(false);
        }
    }

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

    /**
     * Load stored connection settings
     */
    private void loadSettings() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        address = preferences.getString(KEY_ADDRESS, address);
        port = preferences.getInt(KEY_PORT, port);
        userName = preferences.getString(KEY_USERNAME, userName);
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
        editor.putString(KEY_USERNAME, userName);
        editor.putString(KEY_PASS, password);
        editor.apply();
    }

    /**
     * Display connections settings menu where server address, port, user name
     * and password are entered
     */
    private void showSettings() {
        final View view = getLayoutInflater().inflate(R.layout.connection_form, null);
        final EditText inputUrl = view.findViewById(R.id.txt_url);
        final EditText inputPort = view.findViewById(R.id.txt_port);
        final EditText inputUser = view.findViewById(R.id.txt_user);
        final EditText inputPassword = view.findViewById(R.id.txt_password);

        inputUrl.setText(address);
        inputPort.setText(String.valueOf(port));
        inputUser.setText(userName);
        inputPassword.setText(password);

        new AlertDialog.Builder(this)
                .setTitle(R.string.connection_settings)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                            address = inputUrl.getText().toString();
                            String inputPortText = inputPort.getText().toString();
                            if (!inputPortText.isEmpty()) {
                                port = Integer.parseInt(inputPortText);
                            }
                            userName = inputUser.getText().toString();
                            password = inputPassword.getText().toString();
                            saveSettings();
                            disconnect(app.mipSdkMobile);
                            connect();
                        }
                )
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    /**
     * Display application name and version number
     */
    private void showAboutDlg() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_about_message)
                .setTitle(R.string.app_long_name)
                .create()
                .show();
    }

    private void disconnect(MIPSDKMobile sdk) {
        new Thread(() -> {
            if (sdk != null) {
                sdk.closeCommunication();
                showStatus(getString(R.string.status_disconnected));
            }
        }).start();
    }

    private void connect() {
        initSdk();
        // Start the connection sequence on a separate thread so not to block the UI
        showStatus(getString(R.string.connecting));

        new Thread(() -> {
            try {
                app.mipSdkMobile.connect(response -> {
                    // Connected. Enable login button.
                    showStatus(getString(R.string.connected));
                    showLoginButton(true);
                }, response -> {
                    showStatus(getString(R.string.connect_fail));
                    showLoginButton(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                showStatus(getString(R.string.connect_fail));
                showLoginButton(false);
            }
        }).start();
    }

    private void initSdk() {
        URL connectionUrl;

        try {
            connectionUrl = new URL(COMMUNICATION_PROTOCOL, address, port, "");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            showStatus(getString(R.string.bad_address));
            return;
        }

        app.mipSdkMobile = new MIPSDKMobile(this, connectionUrl, COMMUNICATION_ALIAS,
                null, null, null,
                null, false);
    }

    private void showLoginButton(final boolean hasToShow) {
        MainActivity.this.runOnUiThread(() -> {
            if (hasToShow) {
                loginButton.setVisibility(View.VISIBLE);
                loginButton.setEnabled(true);
                connectButton.setVisibility(View.GONE);
            } else {
                loginButton.setVisibility(View.GONE);
                connectButton.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Status message overlay. Can be called from any thread.
     *
     * @param msg Message text to display
     */
    private void showStatus(final String msg) {
        runOnUiThread(() -> {
            if (statusView != null) {
                statusView.setText(msg);
            }
        });
    }

    /**
     * Log in to the connected server (use stored user credentials)
     */
    private void logIn() {
        showStatus(getString(R.string.logging_in));

        new Thread(() -> app.mipSdkMobile.logIn(userName, password, response -> {
            Intent videoPush = new Intent(MainActivity.this, VideoPushActivity.class);
            startActivityForResult(videoPush, REQUEST_CODE_CAMERA);
        }, response -> runOnUiThread(() -> showStatus(getString(R.string.login_fail))))).start();
    }
}
