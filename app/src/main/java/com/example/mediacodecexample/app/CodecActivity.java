package com.example.mediacodecexample.app;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class CodecActivity extends ActionBarActivity implements SurfaceHolder.Callback {

    private MediaCodec mediaCodec;
    private BufferedOutputStream outputStream;
    private Camera camera;
    private SurfaceView surfaceView;
    private FrameLayout frame;

    private final static int maximumWaitTimeForCamera = 5000;
    private final static String TAG = "CodecActivity";
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private static final long DURATION_SEC = 8;

    private BufferedOutputStream fos;

    private byte[]  mBuffer;
    private int N = 0;

    private static MediaFormat format;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codec);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame = (FrameLayout) this.findViewById(R.id.camera_preview);
        startTime = System.currentTimeMillis();

        setUp();
        surfaceView = (SurfaceView) this.findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(this);
        SurfaceView dummy = new SurfaceView(this);
        //frame.addView(surfaceView);
        frame.addView(dummy);
    }

    @Override
    public void onDestroy() {
        if(mediaCodec != null)
            close();
        super.onDestroy();
    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
            closeOutputFile();
            camera.stopPreview();
            camera.release();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void closeOutputFile() {
        try {
            fos.flush();
            fos.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setUp() {
        int encBitRate = 125000;

        format = MediaFormat.createVideoFormat("video/avc", 320, 240);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, encBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 320 * 240);

        createOutputFile();
        initMediaCodec();
    }

    private void initMediaCodec() {
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
    }

    private void createOutputFile() {
        int fileNumber = N / 300;
        File dir = new File("/sdcard/wearscript_video/" + startTime + "/");
        dir.mkdir();
        File f = new File("/sdcard/wearscript_video/" + startTime + "/" + fileNumber + ".h264");
        try {
            fos = new BufferedOutputStream(new FileOutputStream(f));
            Log.i("AvcEncoder", "outputStream initialized");
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    public synchronized void offerEncoder(byte[] input) {

        N++;
        if (N % 300 == 0) {
            closeOutputFile();
            createOutputFile();
            initMediaCodec();
        }

        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();// here changes
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            Log.d("SIZE"," "+input.length);
            inputBuffer.clear();
            inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0,0);
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do
        {
            if (outputBufferIndex >= 0)
            {
                ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
                System.out.println("buffer info-->" + bufferInfo.offset + "--"
                        + bufferInfo.size + "--" + bufferInfo.flags + "--"
                        + bufferInfo.presentationTimeUs);
                byte[] outData = new byte[bufferInfo.size];
                outBuffer.get(outData);
                try
                {
                    if (bufferInfo.offset != 0)
                    {
                        fos.write(outData, bufferInfo.offset, outData.length
                                - bufferInfo.offset);
                    }
                    else
                    {
                        fos.write(outData, 0, outData.length);
                    }
                    fos.flush();
                    Log.i(TAG, "out data -- > " + outData.length);
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,
                            0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                outputBuffers = mediaCodec.getOutputBuffers();
            }
            else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
            {
                MediaFormat format = mediaCodec.getOutputFormat();
            }
        } while (outputBufferIndex >= 0);
    }

    private Camera getCameraInstanceRetry() {
        Camera c = null;
        boolean acquiredCam = false;
        int timePassed = 0;
        while (!acquiredCam && timePassed < maximumWaitTimeForCamera) {
            try {
                c = Camera.open();
                acquiredCam = true;
                return c;
            } catch (Exception e) {
                Log.e(TAG, "Exception encountered opening camera:" + e.getLocalizedMessage());
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ee) {
                Log.e(TAG, "Exception encountered sleeping:" + ee.getLocalizedMessage());
            }
            timePassed += 200;
        }
        return c;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (camera == null){
            camera = getCameraInstanceRetry();
        }
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e){

        }

        Camera.Parameters camParams = camera.getParameters();
        int size = 320 * 240;
        camParams.setPreviewSize(320,240);
        camParams.setPreviewFpsRange(5000, 5000);
        size  = size * ImageFormat.getBitsPerPixel(camParams.getPreviewFormat()) / 8;
        mBuffer = new byte[size]; // class variable
        camera.setParameters(camParams);
        camera.addCallbackBuffer(mBuffer);
        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] bytes, Camera camera) {
                offerEncoder(bytes);
            }
        });

        camera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //Do nothing
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //Do nothing
    }





}
