package com.milestonesys.mobilesdk.videopushsample;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand;
import com.milestonesys.mipsdkmobile.communication.ConnectivityStateReceiver;
import com.milestonesys.mipsdkmobile.communication.HTTPConnection;
import com.milestonesys.mipsdkmobile.communication.VideoCommand;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.milestonesys.mobilesdk.videopushsample.MainActivity.REQUEST_PERMISSIONS_DENIED;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ALIAS_VIDEO;

public class VideoPushActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ASK_PERMISSIONS = 124;
    private static final int DEFAULT_RESOLUTION_WIDTH = 1920;
    private static final int DEFAULT_RESOLUTION_HEIGHT = 1020;
    private static final String NUMBER_OF_AUDIO_CHANNELS = "1";

    private VideoPushApplication app = null;

    private VideoCommand vCmd = null;
    private String videoId = null;
    private VideoPushHelper videoPushHelper = null;
    private PushToTalkHelper pushToTalkHelper = null;
    private HTTPConnection<HttpURLConnection> conn = null;
    private boolean isPushingVideo = false;
    private boolean isRequesting = false;

    private Button startVideoPushButton, stopVideoPushButton;
    private PreviewView cameraPreview = null;
    private OrientationEventListener orientationEventListener;
    private SwitchCompat audioSwitch;
    private View recordingInProgressView;

    private final Animation animation = new AlphaAnimation(0.0F, 1.0F);

    private Preview previewUseCase;
    private ImageAnalysis imageAnalysisUseCase;
    private ExecutorService analysisExecutor;

    private ProcessCameraProvider cameraProvider;
    private int lastAppliedRotation = -1;
    private boolean suppressAudioSwitchCallback = false;
    private static final Size targetResolution = new Size(DEFAULT_RESOLUTION_WIDTH, DEFAULT_RESOLUTION_HEIGHT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_push);

        setupViews();
        setupAnimation();
    }


    @Override
    public void onResume() {
        super.onResume();

        videoPushHelper = new VideoPushHelper(app.mipSdkMobile);
        enableRotationUpdates();

        if (cameraProvider == null) {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    checkPermission();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, ContextCompat.getMainExecutor(this));
        } else {
            checkPermission();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        disableRotationUpdates();

        releaseCamera();
        stopStream();
        showStartButton();

        setAudioSwitchCheckedSilently(false);
        audioSwitch.setEnabled(false);
        vCmd = null;
    }

    private void setupViews() {
        app = (VideoPushApplication) getApplication();

        startVideoPushButton = findViewById(R.id.start_video_push_button);
        stopVideoPushButton = findViewById(R.id.stop_video_push_button);
        audioSwitch = findViewById(R.id.audio_switch);
        recordingInProgressView = findViewById(R.id.recording_view);

        cameraPreview = findViewById(R.id.camera_preview_view);

        audioSwitch.setChecked(false);
        audioSwitch.setEnabled(false);
        assignClickListeners();
    }

    /**
     * Sets up blinking animation to the recording view in order to show
     * that video is being pushed.
     */
    private void setupAnimation() {
        animation.setDuration(500);
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.setRepeatCount(ValueAnimator.INFINITE);
    }

    /**
     * Check permissions for Camera and Microphone and ask for them if not given.
     */
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            if (!isRequesting) {
                isRequesting = true;
                ArrayList<String> permissionsToAskFor = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                    permissionsToAskFor.add(Manifest.permission.CAMERA);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    permissionsToAskFor.add(Manifest.permission.RECORD_AUDIO);
                }
                requestPermissions(permissionsToAskFor.toArray(new String[0]), REQUEST_CODE_ASK_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                setResult(REQUEST_PERMISSIONS_DENIED);
                finish();
            }
            isRequesting = false;
        }
    }

    /**
     * Assign click listeners to the corresponding buttons and
     * handle stop/start video push and audio push
     */
    private void assignClickListeners() {
        startVideoPushButton.setOnClickListener(v -> {
            showStopButton();
            requestVideoStream();
        });

        stopVideoPushButton.setOnClickListener(v -> {
            vCmd = null;
            showStartButton();
            setAudioSwitchCheckedSilently(false);
            audioSwitch.setEnabled(false);
            stopStream();
        });

        audioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAudioSwitchCallback) return;

            final boolean userInitiated = buttonView.isPressed();

            if (!isChecked) {
                boolean wasRecording = (pushToTalkHelper != null && pushToTalkHelper.isRecording());
                stopPushToTalk();

                if (userInitiated && wasRecording) {
                    Toast.makeText(VideoPushActivity.this,
                            getString(R.string.audio_record_stop), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (!isPushingVideo || videoId == null) {
                setAudioSwitchCheckedSilently(false); // avoid re-entrant stop toast
                return;
            }

            requestStreamAndStartPTT(videoId);
            if (userInitiated) {
                Toast.makeText(VideoPushActivity.this,
                        getString(R.string.audio_record_start), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Show start button and hide stop button and recording view
     */
    private void showStartButton() {
        startVideoPushButton.setVisibility(View.VISIBLE);
        stopVideoPushButton.setVisibility(View.GONE);
        recordingInProgressView.clearAnimation();
        recordingInProgressView.setVisibility(View.GONE);
    }

    /**
     * Show stop button & recording view and hide start button
     */
    private void showStopButton() {
        startVideoPushButton.setVisibility(View.GONE);
        stopVideoPushButton.setVisibility(View.VISIBLE);
        recordingInProgressView.startAnimation(animation);
        recordingInProgressView.setVisibility(View.VISIBLE);
    }

    /**
     * Get the device's default camera.
     */
    private void openCamera() {
        // If the front camera needs to be used the lensFacing should assign CameraSelector.DEFAULT_FRONT_CAMERA
        CameraSelector lensFacing = CameraSelector.DEFAULT_BACK_CAMERA;

        // Unbind all use cases and bind them again with the new lens facing configuration
        if (getCamerasExistence() != 0) {
            new Handler().post(() -> {
                cameraProvider.unbindAll();
                bindCameraUseCases(lensFacing);
            });
        }
    }

    private void bindCameraUseCases(CameraSelector cameraSelector) {
        if (cameraProvider == null) return;

        int rotation = (cameraPreview.getDisplay() != null)
                ? cameraPreview.getDisplay().getRotation()
                : Surface.ROTATION_0;

        previewUseCase = new Preview.Builder()
                .setTargetRotation(rotation)
                .setTargetResolution(targetResolution)
                .build();
        previewUseCase.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        imageAnalysisUseCase = new ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        if (analysisExecutor == null || analysisExecutor.isShutdown() || analysisExecutor.isTerminated()) {
            analysisExecutor = Executors.newSingleThreadExecutor();
        }

        imageAnalysisUseCase.setAnalyzer(analysisExecutor, new FrameGrabber());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, imageAnalysisUseCase);

        enableRotationUpdates();
    }

    private void enableRotationUpdates() {
        if (orientationEventListener != null) return;

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (cameraPreview == null || cameraPreview.getDisplay() == null) return;

                int rotation = cameraPreview.getDisplay().getRotation();

                if (rotation == lastAppliedRotation) return;
                lastAppliedRotation = rotation;

                if (previewUseCase != null) previewUseCase.setTargetRotation(rotation);
                if (imageAnalysisUseCase != null) imageAnalysisUseCase.setTargetRotation(rotation);
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    private void disableRotationUpdates() {
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
        lastAppliedRotation = -1;
    }

    private void releaseCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (imageAnalysisUseCase != null) {
            imageAnalysisUseCase.clearAnalyzer();
        }

        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
            analysisExecutor = null;
        }
    }

    /**
     * Starts push to talk
     *
     * @param streamId The stream ID acquired from the response withing {@link #requestStreamAndStartPTT(String)}
     */
    private void startPushToTalk(String streamId) {
        if (pushToTalkHelper == null) {
            pushToTalkHelper = new PushToTalkHelper(VideoPushActivity.this, streamId, null);
        }

        if (!pushToTalkHelper.isRecording()) {
            pushToTalkHelper.startRecording(this);
        }
    }

    /**
     * Stops the push to talk.
     */
    private void stopPushToTalk() {
        if (pushToTalkHelper == null) return;

        if (pushToTalkHelper.isRecording()) {
            pushToTalkHelper.stopRecording();
        }
    }

    /**
     * Requests stream id from the MoS and starts pushing audio frames.
     */
    private void requestStreamAndStartPTT(final String speakerId) {
        if (speakerId == null) return;
        new Thread("RequestAudio") {
            public void run() {
                app.mipSdkMobile.requestAudioPush(speakerId, PushToTalkHelper.AUDIO_ENCODING,
                        PushToTalkHelper.RECORDER_SAMPLE_RATE, PushToTalkHelper.BITS_PER_SAMPLE, NUMBER_OF_AUDIO_CHANNELS,
                        communicationCommand -> {
                            String streamId = communicationCommand.getOutputParam().get(CommunicationCommand.PARAM_STREAM_ID);
                            startPushToTalk(streamId);
                        }, communicationCommand -> VideoPushActivity.this.runOnUiThread(() -> {
                            setAudioSwitchCheckedSilently(false);
                            Toast.makeText(VideoPushActivity.this,
                                    getString(R.string.error_request_stream), Toast.LENGTH_SHORT).show();
                        }));
            }
        }.start();
    }

    public int getCamerasExistence() {
        int cameras = 0;

        try {
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                cameras |= 1;
            }
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                cameras |= 2;
            }
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        return cameras;
    }

    private void setAudioSwitchCheckedSilently(boolean checked) {
        if (audioSwitch == null) return;
        suppressAudioSwitchCallback = true;
        audioSwitch.setChecked(checked);
        suppressAudioSwitchCallback = false;
    }

    private class FrameGrabber implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            try {
                videoPushHelper.sendData(image, vCmd, conn, VideoPushActivity.this::stopStream);
            } finally {
                image.close();
            }
        }
    }

    /**
     * Stop stream by sending request to MIP SDK Mobile
     */
    private void stopStream() {
        new Thread("Stop video push stream") {
            @Override
            public void run() {
                stopPushToTalk();

                final String id = videoId;
                if (id != null && app.mipSdkMobile != null) {
                    app.mipSdkMobile.stopAudioStream(id, null, null);
                    app.mipSdkMobile.stopVideoStream(id, null, null);
                }

                videoId = null;
                isPushingVideo = false;

                runOnUiThread(() -> {
                    setAudioSwitchCheckedSilently(false);
                    audioSwitch.setEnabled(false);
                });
            }
        }.start();
    }

    /**
     * Request video stream by using VideoPushHelper
     * Get video ID from the video properties delivered by VideoPushHelper's request video
     * and handle if any error occurs.
     */
    private void requestVideoStream() {

        new Thread("Request video push stream") {
            public void run() {
                boolean error = false;
                try {
                    HashMap<String, Object> vProps;
                    if (videoId == null) {
                        vProps = videoPushHelper.requestVideo();
                        if (vProps != null && !vProps.containsKey(CommunicationCommand.ERROR_CODE)) {
                            videoId = (String) vProps.get(CommunicationCommand.PARAM_VIDEO_ID);
                        } else {
                            if (vProps != null) {
                                Object errorCode = vProps.get(CommunicationCommand.ERROR_CODE);
                                if (errorCode != null) {
                                    int errCode = (Integer) errorCode;
                                    if (errCode == CommunicationCommand.SERVERERROR_WRONG_CONNECTION_ID) {
                                        stopStream();
                                        ConnectivityStateReceiver.onTimeout();
                                    }
                                }
                            }
                            error = true;
                            return;
                        }
                    }

                    if (videoId != null) {
                        vCmd = new VideoCommand(videoId);
                        try {
                            String videoAlias = ALIAS_VIDEO + videoId + '/';
                            conn = new HTTPConnection<>(app.mipSdkMobile.getSession().getServerHost(),
                                    app.mipSdkMobile.getSession().getServerPort(), videoAlias);
                        } catch (IOException e) {
                            error = true;
                        }
                    } else {
                        error = true;
                    }
                } finally {
                    if (!error) {
                        isPushingVideo = true;
                        VideoPushActivity.this.runOnUiThread(() -> {
                            audioSwitch.setEnabled(true);
                            if (audioSwitch.isChecked()) {
                                requestStreamAndStartPTT(videoId);
                            }
                        });
                    } else {
                        VideoPushActivity.this.runOnUiThread(() -> {
                            Toast.makeText(VideoPushActivity.this, getString(R.string.error_occur), Toast.LENGTH_SHORT).show();
                            showStartButton();
                        });
                    }
                }
            }
        }.start();
    }
}