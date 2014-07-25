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
import java.util.concurrent.LinkedBlockingQueue;


public class CodecActivity extends ActionBarActivity implements SurfaceHolder.Callback {



    private BufferedOutputStream outputStream;
    private Camera camera;
    private SurfaceView surfaceView;
    private FrameLayout frame;


    private MediaMuxer mMuxer;

    private final static int maximumWaitTimeForCamera = 5000;
    private final static String TAG = "CodecActivity";

    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;


    private byte[]  mBuffer;
    private LinkedBlockingQueue<byte[]> rawBuffers = new LinkedBlockingQueue<byte[]>();
    Muxdec muxdec;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codec);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame = (FrameLayout) this.findViewById(R.id.camera_preview);

        muxdec = new Muxdec("/sdcard/muxed.mp4");
        surfaceView = (SurfaceView) this.findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(this);
        SurfaceView dummy = new SurfaceView(this);
        //frame.addView(surfaceView);
        frame.addView(dummy);
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    public void close() {
        muxdec.disconnect();
        try {
            /*mediaCodec.stop();
            mediaCodec.release();
            fos.flush();
            fos.close();*/
            camera.stopPreview();
            camera.release();
        } catch (Exception e){
            e.printStackTrace();
        }
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
            //mThread.setPriority(Thread.MAX_PRIORITY );
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
            Log.d(TAG, "starting CameraHandlerThread()");
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
                    int size = WIDTH * HEIGHT;
                    camParams.setPreviewSize(WIDTH, HEIGHT);
                    size  = size * ImageFormat.getBitsPerPixel(camParams.getPreviewFormat()) / 8;
                    mBuffer = new byte[size]; // class variable
                    camera.setParameters(camParams);
                    camera.addCallbackBuffer(mBuffer);
                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] bytes, Camera camera) {
                            Log.d("CALL","!!!!!!*****!!!!!!******!!!!!");
                            muxdec.add(bytes);
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
        Log.d(TAG, "in surfaceCreated()");
        newOpenCamera();
    }

    /*private void restartMuxer() {
        mMuxer.stop();
        mMuxer.release();
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        bootCodec();

        mMuxer = null;
        try {
            mMuxer = new MediaMuxer("/sdcard/muxed2.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }*/

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //Do nothing
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //Do nothing
    }





}
