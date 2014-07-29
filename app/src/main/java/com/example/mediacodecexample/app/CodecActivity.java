package com.example.mediacodecexample.app;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;

import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;


public class CodecActivity extends ActionBarActivity implements SurfaceHolder.Callback {

    private MediaCodec mediaCodec;
    private MediaCodec audioCodec;
    private MediaFormat mAudioFormat;
    private Camera camera;
    private SurfaceView surfaceView;
    private FrameLayout frame;
    private MediaFormat format;
    private int audioIndex = -1;
    private  AudioRecordThread aRecorder;
    private MediaMuxer mMuxer;
    private final static int maximumWaitTimeForCamera = 5000;
    private final static String TAG = "CodecActivity";
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private BufferedOutputStream fos;
    private byte[]  mBuffer;
    public static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int BIT_RATE_AUDIO = 128000;
    private static final int ENCODING_BIT_RATE = 125000;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex;
    private ArrayList<Pair<Long,byte[]>> mBuffers = new ArrayList<Pair<Long,byte[]>>();
    private int toIndex = -1;
    private Runnable write;
    private static final String TEST_FILE = "/sdcard/muxi.mp4";
    private static final int FPS = 30;
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 240;
    private static final int MAX_AUDIO_INPUT_SIZE =16384;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codec);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame = (FrameLayout) this.findViewById(R.id.camera_preview);

        setUp();
        surfaceView = (SurfaceView) this.findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(this);
        SurfaceView dummy = new SurfaceView(this);
        frame.addView(dummy);
    }

    public void bootVideoCodec() {
        File f = new File("/sdcard/success");
        try {
            fos = new BufferedOutputStream(new FileOutputStream(f));
            Log.i("AvcEncoder", "outputStream initialized");
        } catch (Exception e){
            e.printStackTrace();
        }


        MediaFormat format = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, ENCODING_BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 340 * 480);

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
    }

    @Override
    public void onDestroy() {
            close();
        super.onDestroy();
    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
            fos.flush();
            fos.close();
            camera.stopPreview();
            camera.release();
            mThread.stop();
            aRecorder.stopRecording();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void bootMuxer(String path) {
        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }


    }
    public void bootAudioCodec(){
        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, SAMPLE_RATE, CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_AUDIO_INPUT_SIZE);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_AUDIO);
        audioCodec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
        audioCodec.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();
    }
        public void setUp() {
            bootAudioCodec();
            bootVideoCodec();
            bootMuxer(TEST_FILE);
        }

        public void offerAudioEncoder(Pair<Long,byte []> input, int i) {
            ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();// here changes
            ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();

            int inputBufferIndex = audioCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                Log.d("SIZE"," "+i);
                inputBuffer.clear();
                inputBuffer.put(input.second);
                audioCodec.queueInputBuffer(inputBufferIndex, 0, input.second.length, input.first,0);
            }


            int outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0);
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


                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    if(bufferInfo.size != 0 && audioIndex != -1) {
                        mMuxer.writeSampleData(audioIndex, outBuffer, bufferInfo);
                    }
                    Log.i(TAG, "out data -- > " + outData.length);
                    audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo,
                            0);

                }
                else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
                {
                    outputBuffers = audioCodec.getOutputBuffers();
                }
                else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                {
                    //audioFormat = audioCodec.getOutputFormat();
                    //Log.d("AUDIO","here");
                    //audioIndex = mMuxer.addTrack(audioFormat);
                    //mMuxer.start();
                }
            } while (outputBufferIndex >= 0);
        }

        public void offerVideoEncoder(Pair<Long,byte[]> input, int i) {

            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();// here changes
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input.second);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.second.length, input.first ,0);
            }

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
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0;
                        }
                        if(bufferInfo.size != 0)
                            mMuxer.writeSampleData(trackIndex, outBuffer, bufferInfo);
                        Log.i(TAG, "out data -- > " + outData.length);
                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,
                                0);
                }
                else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
                {
                    outputBuffers = mediaCodec.getOutputBuffers();
                }
                else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                {
                    format = mediaCodec.getOutputFormat();
                    trackIndex = mMuxer.addTrack(format);
                    audioIndex = mMuxer.addTrack(audioCodec.getOutputFormat());
                    mMuxer.start();
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


    private void newOpenCamera() {
        if (mThread == null) {
            mThread = new CameraHandlerThread();
            mThread.setPriority(Thread.MAX_PRIORITY );
        }
        synchronized (mThread) {
            mThread.openCamera();
        }
    }
    private CameraHandlerThread mThread = null;
    private  class CameraHandlerThread extends HandlerThread {
        Handler mHandler = null;
        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run()  {

                    camera = CodecActivity.this.getCameraInstanceRetry();

                    if (camera == null){
                        camera = getCameraInstanceRetry();
                    }
                    try {
                        camera.setPreviewDisplay(surfaceView.getHolder());
                    } catch (IOException e){

                    }

                    Camera.Parameters camParams = camera.getParameters();
                    int size = VIDEO_WIDTH * VIDEO_HEIGHT;
                    camParams.setPreviewSize(VIDEO_WIDTH,VIDEO_HEIGHT);
                    camParams.setPreviewFormat(ImageFormat.NV21);
                    size  = size * ImageFormat.getBitsPerPixel(camParams.getPreviewFormat()) / 8;
                    mBuffer = new byte[size]; // class variable
                    camera.setParameters(camParams);
                    camera.addCallbackBuffer(mBuffer);
                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] bytes, Camera camera) {
                            Log.d("CALL","!!!!!!*****!!!!!!******!!!!!");
                            mBuffers.add(new Pair<Long,byte[]>(System.nanoTime()/1000,NV21toNV12(bytes,VIDEO_WIDTH,VIDEO_HEIGHT)));
                        }
                    });

                    camera.startPreview();
                    aRecorder = new AudioRecordThread(CodecActivity.this,"/sdcard/temp");
                    aRecorder.start();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            }
            catch (InterruptedException e) {
            }
        }
    }

    public byte[] NV21toNV12(byte[] input, int width, int height) {
        int frameSize = width * height;
        Log.d("SHAT",""+input.length);
        byte[] result = new byte[input.length];
        int limit = frameSize/4;
        System.arraycopy(input, 0, result, 0, frameSize);
        for (int i =0; i<limit ; i++) {
            result[frameSize + i*2] = input[frameSize + i*2+1];
            result [frameSize + i*2 +1] = input[frameSize + i*2];
        }
        return result;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        newOpenCamera();

        final Handler stop = new Handler();


            write = new Runnable() {
            @Override
            public void run() {

                toIndex = mBuffers.size();

                camera.stopPreview();
                aRecorder.stopRecording();
                ArrayList<Pair<Long,byte[]>> soundData = aRecorder.getBuffers();
                int size = Math.max(soundData.size(),mBuffers.size());

                for (int i = 0 ; i <size; i++) {
                    Thread.yield();
                    if (i < mBuffers.size())
                        offerVideoEncoder(mBuffers.get(i),i);
                    if (i < soundData.size())
                        offerAudioEncoder(soundData.get(i), i);
                }
                mMuxer.stop();
                mMuxer.release();
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
        };
        stop.postDelayed(write, 5000);
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
