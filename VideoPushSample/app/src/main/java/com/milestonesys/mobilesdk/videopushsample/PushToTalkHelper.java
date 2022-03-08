package com.milestonesys.mobilesdk.videopushsample;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.milestonesys.mipsdkmobile.communication.HTTPConnection;
import com.milestonesys.mipsdkmobile.communication.PushToTalkCommand;
import com.milestonesys.mipsdkmobile.communication.VideoCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import static com.milestonesys.mipsdkmobile.communication.CommunicationCommand.SERVERERROR_INTERNAL_ERROR;
import static com.milestonesys.mobilesdk.videopushsample.VideoPushApplication.ALIAS_AUDIO;

class PushToTalkHelper {
    static final String RECORDER_SAMPLE_RATE = "8000";
    static final String BITS_PER_SAMPLE = "16";
    static final String AUDIO_ENCODING = "Pcm";
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_ELEMENTS_TO_REC = 2048;
    private static final int BYTES_PER_ELEMENT = 2;

    private static final int RESPONSE_READ_TIMEOUT = 3000; //in milliseconds
    private static final int RESPONSE_TIMEOUT = 4000; //in milliseconds

    private AudioRecord recorder = null;
    private boolean isRecording = false;

    private final byte[] buffer = new byte[36];
    private int frameCount = 0;

    private final VideoCommand vCmd;
    private final String streamId;
    private HTTPConnection<HttpURLConnection> communication = null;
    private final VideoPushApplication app;
    private final PushToTalkErrorCallback errorCallback;
    private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();


    PushToTalkHelper(Context cnt, String streamId, PushToTalkErrorCallback errorCallback) {
        this.streamId = streamId;
        this.errorCallback = errorCallback;

        app = (VideoPushApplication) cnt.getApplicationContext();

        try {
            communication = new HTTPConnection<>(app.mipSdkMobile.getSession().getServerHost(),
                    app.mipSdkMobile.getSession().getServerPort(), ALIAS_AUDIO + streamId + '/');
            communication.setMaxTimeForReadingInputStream(RESPONSE_READ_TIMEOUT);
            communication.setMaxWaitingTimeBeforeTimeout(RESPONSE_TIMEOUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        vCmd = new VideoCommand(streamId);
    }

    public boolean isRecording() {
        return isRecording;
    }

    void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                Integer.parseInt(RECORDER_SAMPLE_RATE), RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BUFFER_ELEMENTS_TO_REC * BYTES_PER_ELEMENT);

        recorder.startRecording();
        isRecording = true;

        new Thread(this::catchAudioData).start();
    }

    //convert short to byte
    private byte[] shortToByte(short[] sData) {
        int shortArraySize = sData.length;
        byte[] bytes = new byte[shortArraySize * 2];

        for (int i = 0; i < shortArraySize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void catchAudioData() {
        short[] sData = new short[BUFFER_ELEMENTS_TO_REC];
        try {
            while (isRecording) {
                //gets the voice output from microphone to byte format

                recorder.read(sData, 0, BUFFER_ELEMENTS_TO_REC);
                //writes the data to file from buffer
                //stores the voice buffer
                byte[] bData = shortToByte(sData);
                outStream.write(bData, 0, BUFFER_ELEMENTS_TO_REC * BYTES_PER_ELEMENT);
                final byte[] dataToSend = outStream.toByteArray().clone();
                outStream.reset();
                sendData(dataToSend);
            }
        } catch (OutOfMemoryError e) {
            stopRecording();
        }
    }

    private synchronized void sendData(byte[] data) {
        if (data == null) return;

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        try {
            bOut.write(buffer);
            bOut.write(data);

            byte[] waveBuffer = bOut.toByteArray();
            frameCount++;
            if (vCmd != null) {
                vCmd.refactorMainHeader(waveBuffer, frameCount, System.currentTimeMillis());
            } else {
                stopRecording();
                return;
            }
            if (communication != null) {
                if (communication.sendByteArrayRequest(waveBuffer) != 0) {
                    if (errorCallback != null) {
                        errorCallback.onErrorOccurred(SERVERERROR_INTERNAL_ERROR);
                    }
                    stopRecording();
                } else {
                    int responseResult = getResponseResult(communication.receiveResponse());
                    if (responseResult != 0) {
                        if (errorCallback != null) {
                            errorCallback.onErrorOccurred(responseResult);
                        }
                        stopRecording();
                    }
                }
            }
        } catch (IOException e) {
            stopRecording();
            if (errorCallback != null) {
                errorCallback.onErrorOccurred(SERVERERROR_INTERNAL_ERROR);
            }
            e.printStackTrace();
        } finally {
            try {
                bOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int getResponseResult(InputStream inputStream) {
        int result = 0;
        if (inputStream != null) {
            try {
                byte[] headersBuffer = new byte[40];
                int bytesRead;
                bytesRead = inputStream.read(headersBuffer, 0, headersBuffer.length);
                if (bytesRead != -1) {
                    PushToTalkCommand pttCommand = new PushToTalkCommand(streamId, headersBuffer);
                    short headerFlags = pttCommand.getExtHeaderFlags();

                    if (headerFlags != 0) {
                        //read whole header
                        byte[] bufExtHeader = new byte[pttCommand.getExtHeaderBytesSize() - headersBuffer.length];
                        bytesRead = 0;
                        while (bytesRead != bufExtHeader.length) {
                            bytesRead += inputStream.read(bufExtHeader, bytesRead, bufExtHeader.length - bytesRead);
                            if (bytesRead == -1) break;
                        }

                        if ((PushToTalkCommand.HEADER_EXTENTION_DYNAMIC_INFO & headerFlags) == PushToTalkCommand.HEADER_EXTENTION_DYNAMIC_INFO) {
                            pttCommand.setHeaderDynamicInfo(bufExtHeader, 0);
                            result = pttCommand.getHeaderDeviceStateInfo().getErrorCode();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public synchronized void stopRecording() {
        // stops the recording process
        try {
            if (recorder != null) {
                isRecording = false;
                recorder.stop();
                recorder.release();
                recorder = null;
                new Thread(() -> {
                    if (streamId != null) {
                        if (app.mipSdkMobile != null) {
                            app.mipSdkMobile.stopAudioStream(streamId, null, null);
                        }
                    }
                }).start();
                if (communication != null) {
                    communication.closeConnection();
                    communication = null;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    interface PushToTalkErrorCallback {
        void onErrorOccurred(int errorCode);
    }
}

