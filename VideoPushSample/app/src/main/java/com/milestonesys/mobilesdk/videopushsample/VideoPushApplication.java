package com.milestonesys.mobilesdk.videopushsample;

import android.app.Application;

import com.milestonesys.mipsdkmobile.MIPSDKMobile;

public class VideoPushApplication extends Application {

    public static final String COMMUNICATION_PROTOCOL = "http";
    // Communication paths (appended to server address; normally shouldn't be changed)
    public static final String COMMUNICATION_ALIAS = "XProtectMobile";
    public static final String ALIAS_AUDIO = COMMUNICATION_ALIAS + "/Audio/";
    public static final String ALIAS_VIDEO = COMMUNICATION_ALIAS + "/Video/";
    
    public static final String ERROR_CONNECTION_REFUSED = "Connection refused";
    public static final String ERROR_CONNECTION_RESET = "Connection reset";
    public static final String ERROR_BAD_REQUEST = "Bad Request";
    
    public MIPSDKMobile mipSdkMobile;
}
