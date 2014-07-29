package com.example.mediacodecexample.app;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;




public class CodecActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private FrameLayout frame;
    private final static String TAG = "CodecActivity";
    private TakeTwoMediaRecorder customRecorder;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codec);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame = (FrameLayout) this.findViewById(R.id.camera_preview);
        surfaceView = (SurfaceView) this.findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(this);
        SurfaceView dummy = new SurfaceView(this);
        frame.addView(dummy);
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    public void close() {
        customRecorder.stop();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "in surfaceCreated()");
        customRecorder = new TakeTwoMediaRecorder(this,surfaceView);
        customRecorder.start();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                customRecorder.stop();
            }
        },6000);

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
