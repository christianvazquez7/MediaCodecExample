package com.example.mediacodecexample.app;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class Muxdec {
    private static final String TAG = "Muxdec";

    private boolean connected = true;
    private LinkedBlockingQueue<byte[]> rawBuffers;
    private MediaCodec mediaCodec;
    private MediaMuxer mMuxer;
    private MediaCodec audioCodec;
    private MediaFormat mAudioFormat;

    private int N = 1;

    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex;

    private MediaFormat format;

    // Audio Parameters
    public static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int BIT_RATE_AUDIO = 128000;
    public static final int SAMPLES_PER_FRAME = 1024; // AAC
    public static final int FRAMES_PER_BUFFER = 24;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;

    // Video Parameters
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames


    public Muxdec(String fileName) {
        rawBuffers = new LinkedBlockingQueue<byte[]>();
        setUp(fileName);

        final Handler streamRawToCodec = new Handler();
        streamRawToCodec.post(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    if (rawBuffers.size() > 0) {
                        offerEncoder(rawBuffers.remove());
                    } else if (!connected) {
                        break;
                    } else {
                        Thread.yield();
                        try {
                            Thread.sleep(50);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Thread.yield();
                }
            }
        });
    }

    public void add(byte[] input) {
        rawBuffers.add(input);
    }

    public void prepareEncoder() {
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

    public void setUp(String path) {
        int encBitRate = 125000;

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", CodecActivity.WIDTH, CodecActivity.HEIGHT);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, encBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 320 * 240);

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    public void offerEncoder(byte[] input) {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();// here changes
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            Log.d("SIZE", " " + input.length);
            inputBuffer.clear();
            inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, N*1000*1000/FRAME_RATE,0);
            N++;
        }

        Thread.yield();

        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do
        {
            Thread.yield();
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
                mMuxer.start();
            }
        } while (outputBufferIndex >= 0);
    }

    public void disconnect() {
        connected = false;
    }
}
