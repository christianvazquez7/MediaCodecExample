package com.example.mediacodecexample.app;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.RenderScript;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
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
import java.util.ArrayList;


public class CodecActivity extends ActionBarActivity implements SurfaceHolder.Callback {

    private MediaCodec mediaCodec;
    private MediaCodec audioCodec;
    private MediaFormat mAudioFormat;
    private BufferedOutputStream outputStream;
    private Camera camera;
    private SurfaceView surfaceView;
    private FrameLayout frame;
    private MediaFormat format;

    private MediaMuxer mMuxer;

    private final static int maximumWaitTimeForCamera = 5000;
    private final static String TAG = "CodecActivity";
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private static final long DURATION_SEC = 8;

    private BufferedOutputStream fos;

    private byte[]  mBuffer;
    private int N =1;

    public static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int BIT_RATE_AUDIO = 128000;
    public static final int SAMPLES_PER_FRAME = 1024; // AAC
    public static final int FRAMES_PER_BUFFER = 24;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex;
    private boolean isStarted = false;
    private ArrayList<byte[]> mBuffers = new ArrayList<byte[]>();
    private int fromIndex = 0;
    private int toIndex = -1;
    private Runnable write;


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
        //frame.addView(surfaceView);
        frame.addView(dummy);
    }

    @Override
    public void onDestroy() {
        if(mediaCodec != null)
            close();
        super.onDestroy();
    }

    private  void muxFiles() {

    }

    public void close() {
        try {
            muxFiles();
            mediaCodec.stop();
            mediaCodec.release();
            fos.flush();
            fos.close();
            camera.stopPreview();
            camera.release();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void prepareEncoder(){
        // prepare audio format
        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, SAMPLE_RATE, CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_AUDIO);

        audioCodec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
        audioCodec.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();

        //new Thread(new AudioEncoderTask(), "AudioEncoderTask").start();
    }
        public void setUp() {
            File f = new File("/sdcard/success");
            try {
                fos = new BufferedOutputStream(new FileOutputStream(f));
                Log.i("AvcEncoder", "outputStream initialized");
            } catch (Exception e){
                e.printStackTrace();
            }

            int encBitRate = 125000;

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 320, 240);

            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, encBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 320 * 240);

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

            try {
                mMuxer = new MediaMuxer("/sdcard/muxed.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException ioe) {
                throw new RuntimeException("MediaMuxer creation failed", ioe);
            }

        }


        public void offerEncoder(byte[] input, int i) {

            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();// here changes
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                Log.d("SIZE"," "+input.length);
                inputBuffer.clear();
                inputBuffer.put(input);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime()/300 ,0);
                N++;
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
                    isStarted = true;
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
                    int size = 320 * 240;
                    camParams.setPreviewSize(320,240);
                    size  = size * ImageFormat.getBitsPerPixel(camParams.getPreviewFormat()) / 8;
                    mBuffer = new byte[size]; // class variable
                    camera.setParameters(camParams);
                    camera.addCallbackBuffer(mBuffer);
                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] bytes, Camera camera) {
                            Log.d("CALL","!!!!!!*****!!!!!!******!!!!!");
                            mBuffers.add(bytes);
                        }
                    });

                    camera.startPreview();
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


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        newOpenCamera();

        final Handler stop = new Handler();


            write = new Runnable() {
            @Override
            public void run() {
                // Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                toIndex = mBuffers.size();
                for (int i = fromIndex ; i<toIndex ; i++){
                    Thread.yield();
                    offerEncoder(mBuffers.get(i), i);
                }
                fromIndex = toIndex;
                mMuxer.stop();
                mMuxer.release();

                mMuxer = null;
                try {
                    mMuxer = new MediaMuxer("/sdcard/muxed2.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                } catch (IOException ioe) {
                    throw new RuntimeException("MediaMuxer creation failed", ioe);
                }
                trackIndex = mMuxer.addTrack(format);
                mMuxer.start();
                stop.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        toIndex = mBuffers.size();
                        Log.d("FROM",""+fromIndex);
                        for (int i = 0 ; i<toIndex ; i++){
                            offerEncoder(mBuffers.get(i),i);
                        }
                        fromIndex = toIndex;
                        mMuxer.stop();
                        mMuxer.release();

                        mMuxer = null;
                        try {
                            mMuxer = new MediaMuxer("/sdcard/muxed3.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        } catch (IOException ioe) {
                            throw new RuntimeException("MediaMuxer creation failed", ioe);
                        }
                        trackIndex = mMuxer.addTrack(format);
                        mMuxer.start();
                    }
                },5000);


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
