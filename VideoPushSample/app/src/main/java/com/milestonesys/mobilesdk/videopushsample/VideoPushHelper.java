package com.milestonesys.mobilesdk.videopushsample;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import com.milestonesys.mipsdkmobile.MIPSDKMobile;
import com.milestonesys.mipsdkmobile.communication.CommunicationCommand;
import com.milestonesys.mipsdkmobile.communication.ConnectionLayer;
import com.milestonesys.mipsdkmobile.communication.VideoCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.COMMUNICATION_PROTOCOL;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ERROR_BAD_REQUEST;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ERROR_CONNECTION_REFUSED;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ERROR_CONNECTION_RESET;

class VideoPushHelper {

    private final MIPSDKMobile mipSdkMobile;
    private static final String FPS = "8.0";
    private static final String QUALITY = "73";
    private static final int IMAGE_QUALITY = 89;

    private int frameCount = 0;
    private final byte[] headerBuffer = new byte[36];


    VideoPushHelper(MIPSDKMobile mobileSDK) {
        mipSdkMobile = mobileSDK;
    }

    /**
     * Requests video stream from the MIP SDK Mobile which needs information such as
     * video FPS, video quality, properties etc.
     *
     * @return VideoProperties as response to be handled later.
     */
    HashMap<String, Object> requestVideo() {
        final HashMap<String, Object> videoProperties = new HashMap<>();

        mipSdkMobile.requestVideoStream(null, FPS, QUALITY, null, CommunicationCommand.PARAM_SIGNAL_UPLOAD,
                CommunicationCommand.PARAM_METHOD_PULL, CommunicationCommand.PARAM_STREAM_TYPE_TRANSCODED,
                response -> requestVideoSuccessHandling(response, videoProperties), cmd -> requestVideoErrorHandling(cmd, videoProperties));

        return videoProperties;
    }

    /**
     * Handles successful video stream response. And assigns the information to the {@param videoProperties} in order to be used later
     */
    private void requestVideoSuccessHandling(com.milestonesys.mipsdkmobile.communication.CommunicationCommand response, HashMap<String, Object> videoProperties) {
        if (response != null) {
            HashMap<String, String> outParams = response.getOutputParam();
            String videoID = outParams.get(CommunicationCommand.PARAM_VIDEO_ID);

            if (!CommunicationCommand.RESULT_ERROR.equals(response.getResult())) {
                videoProperties.put(CommunicationCommand.PARAM_VIDEO_ID, videoID);
                videoProperties.put(CommunicationCommand.PARAM_PROTOCOL_TYPE, COMMUNICATION_PROTOCOL);
                videoProperties.put(CommunicationCommand.PARAM_SRC_WIDTH, outParams.get(CommunicationCommand.PARAM_SRC_WIDTH));
                videoProperties.put(CommunicationCommand.PARAM_SRC_HEIGHT, outParams.get(CommunicationCommand.PARAM_SRC_HEIGHT));
                videoProperties.put(CommunicationCommand.PARAM_RESIZE_ORIGINAL_SIZE, outParams.get(CommunicationCommand.PARAM_RESIZE_ORIGINAL_SIZE));
            }
        }
    }

    /**
     * Assign error information to the VideoProperties {@param properties} in order to be handled later.
     * Use {@param cmd} in order to check if the incoming result is ERROR.
     */
    private void requestVideoErrorHandling(com.milestonesys.mipsdkmobile.communication.CommunicationCommand cmd, HashMap<String, Object> properties) {
        if (cmd != null) {
            if (CommunicationCommand.RESULT_ERROR.equalsIgnoreCase(cmd.getResult())) {
                properties.put(CommunicationCommand.RESULT_ERROR, cmd.getError());
                properties.put(CommunicationCommand.ERROR_CODE, cmd.getErrorCode());
            }
        }
    }

    void sendData(ImageProxy image, VideoCommand vCmd, ConnectionLayer conn, VideoPushCallback callback) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        try {
            bOut.write(headerBuffer); // adding empty header
            if (vCmd != null && vCmd.headerLocation != null && vCmd.headerLocation.getMask() != 0) {
                bOut.write(vCmd.headerLocation.getData());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (image.getFormat() == ImageFormat.JPEG) {   // This is the format used by ImageCapture
            byte[] bytes = new byte[image.getPlanes()[0].getBuffer().limit()];
            image.getPlanes()[0].getBuffer().get(bytes);

            try {
                bOut.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {  // This is the format used by ImageAnalyzer
            byte[] nv21 = convertImage(image);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), IMAGE_QUALITY, bOut);
        } else {
            return;
        }

        byte[] frameBuffer;
        if (vCmd == null) {
            return;
        } else {
            frameBuffer = bOut.toByteArray();
            frameCount++;
            vCmd.refactorMainHeader(frameBuffer, frameCount, System.currentTimeMillis());
        }

        if (conn.sendByteArrayRequest(frameBuffer) != 0) {
            callback.call();
        } else {
            try {
                conn.receiveResponse();
            } catch (Exception e) {
                String exMsg = e.getMessage();

                if (exMsg.contains(ERROR_CONNECTION_REFUSED) || exMsg.contains(ERROR_CONNECTION_RESET)) {
                    callback.call();
                } else if (!exMsg.contains(ERROR_BAD_REQUEST)) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Convert the image returned from image analyzer
     * from YUV_420_888 to nv21 byte array taking into account the strides
     */
    private static byte[] convertImage(ImageProxy image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();

        int pos = 0;

        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else {
            int yBufferPos = -rowStride; // not an actual position
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position(yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            vBuffer.put(1, (byte) 0);
            if (uBuffer.get(0) == 0) {
                vBuffer.put(1, (byte) 255);
                if (uBuffer.get(0) == 255) {
                    vBuffer.put(1, savePixel);
                    vBuffer.get(nv21, ySize, uvSize);

                    return nv21; // shortcut
                }
            }

            //the check failed then save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    interface VideoPushCallback {
        void call();
    }
}
