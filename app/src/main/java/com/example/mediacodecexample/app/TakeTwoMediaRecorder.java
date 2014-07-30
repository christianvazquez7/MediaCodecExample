package com.example.mediacodecexample.app;


import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;

public class TakeTwoMediaRecorder {

    private AudioRecordThread aRecorder;
    private Muxdec muxdec;
    private final static String TAG = "TakeTwoMediaRecorder";
    private CameraHandlerThread cameraThread;
    private Runnable poll;
    private Handler recordingHandler = new Handler();
    private  static final String DUMMY_FILE = "/sdcard/muxi.mp4";
    private int fileNumber = 0;

    public TakeTwoMediaRecorder (Context activity, SurfaceView sv) {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        muxdec = new Muxdec(this.generateFileName());
        cameraThread = new CameraHandlerThread(sv);
        //cameraThread.setPriority(Thread.MAX_PRIORITY);
        cameraThread.openCamera();
        aRecorder = new AudioRecordThread(activity,"/sdcard/dummy");
        aRecorder.setPriority(Thread.MAX_PRIORITY);

        poll = new Runnable() {
            @Override
            public void run() {
                Log.d("HERE","here");
                Pair<Long,byte[]> videoBuffer = cameraThread.getFrame();
                Pair<Long,byte[]> audioBuffer = aRecorder.getFrame();


                if (videoBuffer != null) {
                    muxdec.offerEncoder(videoBuffer);
                }
                Thread.yield();
                if (audioBuffer != null) {
                    muxdec.offerAudioEncoder(audioBuffer);
                }


                Log.d("HERE","out");

                recordingHandler.postDelayed(poll, 40);
            }
        };

    }

    public String start() {
        while(!cameraThread.startPreview());
        aRecorder.start();
        recordingHandler.post(poll);
        return DUMMY_FILE;
    }


    public void stop(boolean continueRecording) {
        recordingHandler.removeCallbacks(poll);

        int vSize = cameraThread.size();
        int aSize = aRecorder.size();
        int size = Math.max(aSize, vSize);
        Log.d(TAG,"max: "+size);

        if (!continueRecording) {
            cameraThread.stopPreview();
            aRecorder.stopRecording();
        }

        for (int i = 0 ; i <size; i++) {
            if (i < vSize) {
                muxdec.offerEncoder(cameraThread.getFrame());
                Thread.yield();
            }
            if (i < aSize) {
                muxdec.offerAudioEncoder(aRecorder.getFrame());
                Thread.yield();
            }
        }

        Log.d("HAHA",""+cameraThread.size());
        Log.d("HAHA",""+aRecorder.size());

        muxdec.disconnect();
        if (continueRecording) {
            String fileName = "/sdcard/muxi" + fileNumber + ".mp4";
            muxdec = new Muxdec(fileName);
            recordingHandler.post(poll);
        }
    }

    private String generateFileName() {
        return "/sdcard/muxi.mp4";
    }

}
