package com.milestonesys.mobilesdk.videopushsample;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand;
import com.milestonesys.mipsdkmobile.communication.ConnectivityStateReceiver;
import com.milestonesys.mipsdkmobile.communication.HTTPConnection;
import com.milestonesys.mipsdkmobile.communication.VideoCommand;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static com.milestonesys.mobilesdk.videopushsample.MainActivity.REQUEST_PERMISSIONS_DENIED;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ALIAS_VIDEO;

public class VideoPushActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ASK_PERMISSIONS = 124;
    private static final int DEFAULT_RESOLUTION_WIDTH = 1920;
    private static final int DEFAULT_RESOLUTION_HEIGHT = 1020;
    private static final String NUMBER_OF_AUDIO_CHANNELS = "1";

    private VideoPushApplication app = null;

    private Size resolution;
    private VideoCommand vCmd = null;
    private String videoId = null;
    private VideoPushHelper videoPushHelper = null;
    private PushToTalkHelper pushToTalkHelper = null;
    private HTTPConnection<HttpURLConnection> conn = null;
    private boolean isPushingVideo = false;
    private boolean isRequesting = false;

    private SurfaceTexture surfaceTexture;
    private Button startVideoPushButton, stopVideoPushButton;
    private TextureView cameraPreview = null;
    private Switch audioSwitch;
    private View recordingInProgressView;

    private final Animation animation = new AlphaAnimation(0.0F, 1.0F);

    @SuppressWarnings("WeakerAccess")
    private SurfaceRequest surfaceRequest;

    @SuppressWarnings("WeakerAccess")
    private ListenableFuture<SurfaceRequest.Result> surfaceReleaseFuture;

    private ProcessCameraProvider cameraProvider;
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
    public void onPause() {
        super.onPause();
        releaseCamera();
        stopStream();
        showStartButton();
        audioSwitch.setChecked(false);
        vCmd = null;
    }

    private void setupViews() {
        app = (VideoPushApplication) getApplication();

        startVideoPushButton = findViewById(R.id.start_video_push_button);
        stopVideoPushButton = findViewById(R.id.stop_video_push_button);
        audioSwitch = findViewById(R.id.audio_switch);
        recordingInProgressView = findViewById(R.id.recording_view);

        cameraPreview = findViewById(R.id.camera_texture_view);
        assignClickListeners();

        cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture,
                                                  final int width, final int height) {
                VideoPushActivity.this.surfaceTexture = surfaceTexture;
                tryToProvidePreviewSurface();
            }

            @Override
            public void onSurfaceTextureSizeChanged(final SurfaceTexture surfaceTexture,
                                                    final int width, final int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture surfaceTexture) {
                VideoPushActivity.this.surfaceTexture = null;
                if (surfaceRequest == null && surfaceReleaseFuture != null) {
                    surfaceReleaseFuture.addListener(surfaceTexture::release,
                            ContextCompat.getMainExecutor(cameraPreview.getContext()));
                    return false;
                } else {
                    return true;
                }
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture surfaceTexture) {
            }
        });
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
            stopStream();
        });

        audioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isPushingVideo) {
                if (isChecked) {
                    requestStreamAndStartPTT(videoId);
                    Toast.makeText(VideoPushActivity.this, getString(R.string.audio_record_start), Toast.LENGTH_SHORT).show();
                } else {
                    stopPushToTalk();
                    Toast.makeText(VideoPushActivity.this, getString(R.string.audio_record_stop), Toast.LENGTH_SHORT).show();
                }
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

    private void bindCameraUseCases(CameraSelector lensFacing) {
        // Either aspect ratio, or resolution (not both) has to be set.
        // In this case, the resolution is set
        Preview preview = new Preview.Builder()
                .setTargetRotation(cameraPreview.getDisplay().getRotation())
                .setTargetResolution(targetResolution)
                .build();

        preview.setSurfaceProvider(
                (surfaceRequest) -> {
                    resolution = surfaceRequest.getResolution();
                    if (this.surfaceRequest != null) {
                        this.surfaceRequest.willNotProvideSurface();
                    }
                    this.surfaceRequest = surfaceRequest;
                    this.surfaceRequest.addRequestCancellationListener(
                            ContextCompat.getMainExecutor(cameraPreview.getContext()), () -> {
                                if (this.surfaceRequest != null && this.surfaceRequest == surfaceRequest) {
                                    this.surfaceRequest = null;
                                    surfaceReleaseFuture = null;
                                }
                            });
                    tryToProvidePreviewSurface();
                });

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetRotation(cameraPreview.getDisplay().getRotation())
                .setTargetResolution(targetResolution)
                .build();

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), new FrameGrabber());

        cameraProvider.bindToLifecycle(this, lensFacing, preview, imageAnalysis);
    }

    private void releaseCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
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
            pushToTalkHelper.startRecording();
        }
    }

    /**
     * Stops the push to talk.
     */
    private void stopPushToTalk() {
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
                        }, communicationCommand -> VideoPushActivity.this.runOnUiThread(() ->
                                Toast.makeText(VideoPushActivity.this, getString(R.string.error_request_stream), Toast.LENGTH_SHORT).show()));
            }
        }.start();
    }

    @SuppressWarnings("WeakerAccess")
    void tryToProvidePreviewSurface() {
        if (resolution == null || surfaceTexture == null || surfaceRequest == null) {
            return;
        }
        surfaceTexture.setDefaultBufferSize(resolution.getWidth(), resolution.getHeight());

        final Surface surface = new Surface(surfaceTexture);
        @SuppressLint("RestrictedApi") final ListenableFuture<SurfaceRequest.Result> surfaceReleaseFuture =
                CallbackToFutureAdapter.getFuture(completer -> {
                    surfaceRequest.provideSurface(surface,
                            CameraXExecutors.directExecutor(), completer::set);
                    return "provideSurface[request=" + surfaceRequest + " surface=" + surface
                            + "]";
                });
        this.surfaceReleaseFuture = surfaceReleaseFuture;
        this.surfaceReleaseFuture.addListener(() -> {
            surface.release();
            if (this.surfaceReleaseFuture == surfaceReleaseFuture) {
                this.surfaceReleaseFuture = null;
            }
        }, ContextCompat.getMainExecutor(cameraPreview.getContext()));
        surfaceRequest = null;
        transformPreview(resolution);
    }

    void transformPreview(@NonNull Size resolution) {
        if (resolution.getWidth() == 0 || resolution.getHeight() == 0) {
            return;
        }
        if (cameraPreview.getWidth() == 0 || cameraPreview.getHeight() == 0) {
            return;
        }
        Matrix matrix = new Matrix();
        int left = cameraPreview.getLeft();
        int right = cameraPreview.getRight();
        int top = cameraPreview.getTop();
        int bottom = cameraPreview.getBottom();
        // Compute the preview ui size based on the available width, height and ui orientation.
        int viewWidth = (right - left);
        int viewHeight = (bottom - top);
        int displayRotation = getDisplayRotation();
        Size scaled =
                calculatePreviewViewDimens(
                        resolution, viewWidth, viewHeight, displayRotation);
        // Compute the center of the view.
        int centerX = viewWidth / 2;
        int centerY = viewHeight / 2;
        // Do corresponding rotation to correct the preview direction
        matrix.postRotate(-getDisplayRotation(), centerX, centerY);
        // Compute the scale value for center crop mode
        float xScale = scaled.getWidth() / (float) viewWidth;
        float yScale = scaled.getHeight() / (float) viewHeight;
        if (getDisplayRotation() == 90 || getDisplayRotation() == 270) {
            xScale = scaled.getWidth() / (float) viewHeight;
            yScale = scaled.getHeight() / (float) viewWidth;
        }
        // Only two digits after the decimal point are valid for postScale. Need to get ceiling of two
        // digits floating value to do the scale operation. Otherwise, the result may be scaled not
        // large enough and will have some blank lines on the screen.
        xScale = new BigDecimal(xScale).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
        yScale = new BigDecimal(yScale).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
        // Do corresponding scale to resolve the deformation problem
        matrix.postScale(xScale, yScale, centerX, centerY);
        cameraPreview.setTransform(matrix);
    }

    /**
     * @return One of 0, 90, 180, 270.
     */
    private int getDisplayRotation() {
        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (displayRotation) {
            case Surface.ROTATION_0:
                displayRotation = 0;
                break;
            case Surface.ROTATION_90:
                displayRotation = 90;
                break;
            case Surface.ROTATION_180:
                displayRotation = 180;
                break;
            case Surface.ROTATION_270:
                displayRotation = 270;
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported display rotation: " + displayRotation);
        }
        return displayRotation;
    }

    private Size calculatePreviewViewDimens(
            Size srcSize, int parentWidth, int parentHeight, int displayRotation) {
        int inWidth = srcSize.getWidth();
        int inHeight = srcSize.getHeight();
        if (displayRotation == 0 || displayRotation == 180) {
            // Need to reverse the width and height because of the landscape orientation.
            inWidth = srcSize.getHeight();
            inHeight = srcSize.getWidth();
        }
        int outWidth = parentWidth;
        int outHeight = parentHeight;
        if (inWidth != 0 && inHeight != 0) {
            float vfRatio = inWidth / (float) inHeight;
            float parentRatio = parentWidth / (float) parentHeight;
            // Match shortest sides together.
            if (vfRatio < parentRatio) {
                outWidth = parentWidth;
                outHeight = Math.round(parentWidth / vfRatio);
            } else {
                outWidth = Math.round(parentHeight * vfRatio);
                outHeight = parentHeight;
            }
        }
        return new Size(outWidth, outHeight);
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

    private class FrameGrabber implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            videoPushHelper.sendData(image, vCmd, conn, VideoPushActivity.this::stopStream);
            image.close();
        }
    }

    /**
     * Stop stream by sending request to MIP SDK Mobile
     */
    private void stopStream() {

        new Thread("Stop video push stream") {
            public void run() {
                if (videoId != null) {
                    if (audioSwitch.isChecked()) {
                        app.mipSdkMobile.stopAudioStream(videoId, null, null);
                    }
                    if (videoId != null) {
                        app.mipSdkMobile.stopVideoStream(videoId, null, null);
                        videoId = null;
                    }
                }
                isPushingVideo = false;
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
