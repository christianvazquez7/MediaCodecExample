package com.example.mediacodecexample.app;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;


public class CodecActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private FrameLayout frame;
    private final static String TAG = "CodecActivity";
    private TakeTwoMediaRecorder customRecorder;
    private Handler handler = new Handler();
    private SurfaceView videoView;
    private MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codec);
        mp = MediaPlayer.create(this, Uri.fromFile(new File("/sdcard/merge.mp4")));
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame = (FrameLayout) this.findViewById(R.id.camera_preview);
        surfaceView = (SurfaceView) this.findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(this);
        videoView = (SurfaceView) this.findViewById(R.id.video_surface);
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        });
        videoView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                surfaceHolder.setFixedSize(320, 240);
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                mp.setDisplay(surfaceHolder);
                //mp.prepareAsync();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
        SurfaceView dummy = new SurfaceView(this);
        frame.addView(dummy);
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    public void close() {
        customRecorder.stop(false);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "in surfaceCreated()");
        customRecorder = new TakeTwoMediaRecorder(this,surfaceView);
        customRecorder.start();

        Runnable cut = new Runnable() {
            @Override
            public void run() {
                customRecorder.stop(true);
            }
        };

        //handler.postDelayed(cut, 5000);
        handler.postDelayed(cut, 10000);
        //handler.postDelayed(cut, 15000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                customRecorder.stop(false);
            }
        }, 10000);

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
