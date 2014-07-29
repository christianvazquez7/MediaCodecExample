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

        muxdec = new Muxdec(this.generateFileName());
        cameraThread = new CameraHandlerThread(sv);
        cameraThread.openCamera();
        aRecorder = new AudioRecordThread(activity,"/sdcard/dummy");

        poll = new Runnable() {
            @Override
            public void run() {
                Log.d("HERE","here");
                Pair<Long,byte[]> videoBuffer = cameraThread.getFrame();
                Pair<Long,byte[]> audioBuffer = aRecorder.getFrame();


                if (videoBuffer != null) {
                    muxdec.offerEncoder(videoBuffer);
                }
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


    public void stop() {

        recordingHandler.removeCallbacks(poll);
        cameraThread.stopPreview();
        aRecorder.stopRecording();


        int size = Math.max(aRecorder.size(),cameraThread.size());
        Log.d(TAG,"max: "+size);
        int vSize = cameraThread.size();
        int aSize = aRecorder.size();


        for (int i = 0 ; i <size; i++) {
            if (i < vSize)
                muxdec.offerEncoder(cameraThread.getFrame());
            if (i < aSize)
                muxdec.offerAudioEncoder(aRecorder.getFrame());
        }

        Log.d("HAHA",""+cameraThread.size());
        Log.d("HAHA",""+aRecorder.size());


        muxdec.disconnect();

    }
    public String cutTail() {
        ++fileNumber;
        Muxdec oldMuxdec = muxdec;
        String fileName = "/sdcard/muxi" + fileNumber + ".mp4";
        muxdec = new Muxdec(fileName);
        oldMuxdec.disconnect();
        return fileName;
    }
    private String generateFileName() {
        return "/sdcard/muxi.mp4";
    }

}
