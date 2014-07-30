package com.example.mediacodecexample.app;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Muxdec {
    private static final String TAG = "Muxdec";

    private MediaCodec mediaCodec;
    private MediaMuxer mMuxer;
    private MediaCodec audioCodec;
    private MediaFormat mAudioFormat;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex = -1;
    private int audioIndex= -1;

    private MediaFormat format;
    private boolean finish =false;
    // Audio Parameters
    public static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int BIT_RATE_AUDIO = 128000;
    private static final int ENCODING_BIT_RATE = 125000;
    private static final int MAX_AUDIO_INPUT_SIZE =16384;
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 240;

    // Video Parameters
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames


    public Muxdec(String fileName) {
        setUp(fileName);
    }

    public void bootAudioCodec() {
        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, SAMPLE_RATE, CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_AUDIO_INPUT_SIZE);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_AUDIO);
        audioCodec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
        audioCodec.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();
    }

    public void bootVideoCodec() {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, ENCODING_BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
    }

    public void bootMuxer(String path) {
        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }


    public void setUp(String path) {
        bootAudioCodec();
        bootVideoCodec();
        bootMuxer(path);
    }

    public void offerAudioEncoder(Pair<Long,byte []> input) {
        ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();// here changes
        Thread.yield();
        ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
        Thread.yield();

        int inputBufferIndex = audioCodec.dequeueInputBuffer(-1);
        Thread.yield();
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            Thread.yield();
            inputBuffer.clear();
            Thread.yield();
            inputBuffer.put(input.second);
            Thread.yield();
            audioCodec.queueInputBuffer(inputBufferIndex, 0, input.second.length, input.first,0);
            Thread.yield();
        }


        int outputBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0);
        Thread.yield();
        //Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do
        {
            if (outputBufferIndex >= 0)
            {
                ByteBuffer outBuffer = outputBuffers[outputBufferIndex];


                /*System.out.println("buffer info-->" + bufferInfo.offset + "--"
                        + bufferInfo.size + "--" + bufferInfo.flags + "--"
                        + bufferInfo.presentationTimeUs);*/
                byte[] outData = new byte[bufferInfo.size];
                outBuffer.get(outData);


                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                if(bufferInfo.size != 0 && audioIndex != -1 ) {
                    //Log.d("HAHA","~~~~~~~~~~~~~~~~~~~~~~~~");
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
//                Log.d("HAHA","!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//
//                MediaFormat audioFormat = audioCodec.getOutputFormat();
//                //Log.d("AUDIO","here");
//                audioIndex = mMuxer.addTrack(audioFormat);
//                mMuxer.start();
            }
            Thread.yield();
        } while (outputBufferIndex >= 0);
    }

    public void offerEncoder(Pair<Long,byte[]> input) {

        byte[] toEncode = NV21toNV12(input.second,VIDEO_WIDTH,VIDEO_HEIGHT);
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();// here changes
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(toEncode);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, toEncode.length,input.first,0);
        }

        Thread.yield();

        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        //Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do
        {
            Thread.yield();
            if (outputBufferIndex >= 0)
            {
                ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
                /*System.out.println("buffer info-->" + bufferInfo.offset + "--"
                        + bufferInfo.size + "--" + bufferInfo.flags + "--"
                        + bufferInfo.presentationTimeUs);*/
                byte[] outData = new byte[bufferInfo.size];
                outBuffer.get(outData);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                if(bufferInfo.size != 0  && trackIndex != -1) {
                    mMuxer.writeSampleData(trackIndex, outBuffer, bufferInfo);
                }
                //Log.i(TAG, "out data -- > " + outData.length);
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

    public void disconnect() {
        Log.d(TAG, "disconnect()");
        mediaCodec.stop();
        audioCodec.stop();
        mMuxer.stop();
    }

    public byte[] NV21toNV12(byte[] input, int width, int height) {
        int frameSize = width * height;
        byte[] result = new byte[input.length];
        int limit = frameSize/4;
        System.arraycopy(input, 0, result, 0, frameSize);
        for (int i =0; i<limit ; i++) {
            result[frameSize + i*2] = input[frameSize + i*2+1];
            result [frameSize + i*2 +1] = input[frameSize + i*2];
        }
        return result;
    }
}
