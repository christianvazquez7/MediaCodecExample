package com.example.mediacodecexample.app;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;


public class CameraHandlerThread extends HandlerThread {
    private Camera camera;
    private Handler mHandler;
    private SurfaceView dummy;
    private LinkedBlockingQueue<Pair<Long,byte[]>> mBuffers = new LinkedBlockingQueue<Pair<Long, byte[]>>();
    private byte[] mBuffer;
    private final static int maximumWaitTimeForCamera = 5000;
    private static final String TAG = "CameraHandlerThread";
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 240;


    public CameraHandlerThread(SurfaceView sv) {
        super("CameraHandlerThread");
        dummy = sv;
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

                camera = getCameraInstanceRetry();

                if (camera == null){
                    camera = getCameraInstanceRetry();
                }
                try {
                    camera.setPreviewDisplay(dummy.getHolder());
                } catch (IOException e){

                }

                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

                Camera.Parameters camParams = camera.getParameters();
                int size = VIDEO_WIDTH * VIDEO_HEIGHT;
                camParams.setPreviewSize(VIDEO_WIDTH,VIDEO_HEIGHT);
                camParams.setPreviewFormat(ImageFormat.NV21);
                size  = size * ImageFormat.getBitsPerPixel(camParams.getPreviewFormat()) / 8;
                mBuffer = new byte[size];
                camera.setParameters(camParams);
                camera.addCallbackBuffer(mBuffer);
                camera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        Log.d("CALL","!!!!!!*****!!!!!!******!!!!!");
                        mBuffers.add(new Pair<Long,byte[]>(System.nanoTime()/1000,bytes));
                    }
                });

                notifyCameraOpened();
            }
        });

    }
    public Pair<Long,byte[]> getFrame() {
        if (mBuffers.size()<=0)
            return null;
        else
        return mBuffers.remove();
    }
    private Camera getCameraInstanceRetry() {
        android.hardware.Camera c = null;
        boolean acquiredCam = false;
        int timePassed = 0;
        while (!acquiredCam && timePassed < maximumWaitTimeForCamera) {
            try {
                c = android.hardware.Camera.open();
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

    public int size(){
        return mBuffers.size();
    }
    public void stopPreview(){
        camera.stopPreview();
    }
    public boolean startPreview(){
        if (camera == null){
            return false;
        }
        camera.startPreview();
        return true;
    }

}
