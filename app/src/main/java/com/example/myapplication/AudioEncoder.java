package com.example.myapplication;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioEncoder {
    public String TAG = "AudioEncoder";
    private MediaCodec mAudioEncoder = null;
    private AudioRecord mAudioRecord = null;
    private MediaMuxer mAudioMuxer = null;
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private boolean  encoding = false;
    private MediaCodec.BufferInfo bufferInfo = null;
    private int trackIndex;
    private long startWhen;
    private String outputPath;
    private Socket mSocket = null;
    private ParcelFileDescriptor mFileDescriptor = null;
    private BufferedOutputStream mOutputStream = null;
    ByteBuffer[] inputBuffers = null;
    private Queue<byte[]> mAudioQueue = new LinkedBlockingQueue<>();

    public AudioEncoder(){
        try {
            int min_buffer_size = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int buffer_size = SAMPLES_PER_FRAME * 10;
            if (buffer_size < min_buffer_size)
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

            mAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,       // source
                    SAMPLE_RATE,                         // sample rate, hz
                    CHANNEL_CONFIG,                      // channels
                    AUDIO_FORMAT,                        // audio format
                    buffer_size);                        // buffer size (bytes)

            /////////////////

            MediaFormat format  = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);

            mAudioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.start();
            bufferInfo = new MediaCodec.BufferInfo();
            outputPath = Environment.getExternalStorageDirectory() +"/audio_encoded.264.mp4";
            Log.d(TAG, "path : " + outputPath);
            mAudioMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Thread conThread = new Thread(conSocket);
            conThread.start();
            Log.d(TAG, "init OK");
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        } catch (RuntimeException e) {
            release();
            throw e;
        }

    }
    public void encodeAudio() {
        encoding = true;
        Thread encodeThread = new Thread(encodeRun);
        encodeThread.start();
        Thread drainThread = new Thread(drainRun);
        drainThread.start();
    }
    private Runnable encodeRun = new Runnable() {
        public void run() {
            Log.e(TAG, "encodeRun in");
            try {
                mAudioRecord.startRecording();
                startWhen = System.nanoTime();
                inputBuffers = mAudioEncoder.getInputBuffers();
                Thread feedAudioThread = new Thread(feedAudio);
                feedAudioThread.start();
                while (encoding) {
                    sendAudioToEncoder(false);
                }
                //TODO: Sending "false" because calling signalEndOfInputStream fails on this encoder
            } finally {
                release();
            }
            Log.e(TAG, "encodeRun out");
        }
    };
    private Runnable feedAudio = new Runnable() {
        public void run(){
            while(encoding) {
                byte[] inputBuf = new byte[SAMPLES_PER_FRAME];
                int inputLength = mAudioRecord.read(inputBuf, 0, SAMPLES_PER_FRAME);
                Log.d(TAG, "audio read:" + String.valueOf(inputLength));
                if( inputLength <= 0 ) {
                    Log.e(TAG, "audio read Error");
                    continue;
                }
                mAudioQueue.add(inputBuf);
                Log.d(TAG, "queue size:" + String.valueOf(mAudioQueue.size()));
            }
        }
    };

    public int inputBufferIndex = -1;
    public int inputLength = 0;
    public long presentationTimeUs = 0;
    public void sendAudioToEncoder(boolean endOfStream) {
        // send current frame data to encoder
        Log.d(TAG, "sendAudio in");
        byte[] queueBuf = mAudioQueue.poll();
        if( queueBuf == null) {
            Log.e(TAG, "Queue Empty");
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) {
            }
            return;
        }
        inputLength = queueBuf.length;
        try {
            mOutputStream.write(queueBuf, 0, inputLength);
        } catch(IOException e){
            Log.e(TAG, e.getMessage());
        }
        inputBufferIndex = mAudioEncoder.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0) {
            //ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            //inputBuffer.clear();
            long presentationTimeNs = System.nanoTime();
            //int inputLength = mAudioRecord.read(inputBuffer, SAMPLES_PER_FRAME);
            presentationTimeNs -= (inputLength / SAMPLE_RATE) / 1000000000;
            presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
            //prevOutputPTSUs = bufferInfo.presentationTimeUs;
            if (endOfStream) {
                mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                //mAudioEncoder.getInputBuffer(inputBufferIndex).put(Data);
            } else {
                mAudioEncoder.getInputBuffer(inputBufferIndex).put(queueBuf);
                mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, prevOutputPTSUs, 0);
                Log.d(TAG, "input start");
            }
        } else {
            Log.e(TAG, "index Error");
        }
        Log.d(TAG, "sendAudio out");
    }

    private Runnable drainRun = new Runnable() {
        public void run(){
            Log.e(TAG, "drainRun in");
            try {
                while (encoding) {
                    drain(false);
                }
            } finally {
                release();
            }
            Log.e(TAG, "drainRun out");
        }
    };

    public void drain(boolean endOfStream) {
        //final int TIMEOUT_USEC = 10000;
        final int TIMEOUT_USEC = 0;

        if (endOfStream) {
            mAudioEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
        while (true) {
            Log.d(TAG, "drain in");
            int encoderStatus = mAudioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    /*
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e){
                        Log.e(TAG, e.getMessage());
                    }
                    */
                    break;      // out of while
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // now that we have the Magic Goodies, start the muxer
                MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                trackIndex = mAudioMuxer.addTrack(newFormat);
                mAudioMuxer.start();
                Log.e(TAG, " INFO_OUTPUT_FORMAT_CHANGED: " + newFormat.toString());
            } else if (encoderStatus < 0) {
                // let's ignore it
                break;
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                //checkState(encodedData != null, "encoderOutputBuffer %s was null", encoderStatus);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    bufferInfo.presentationTimeUs = getPTSUs();
                    /*
                    byte[] outData = new byte[bufferInfo.size];
                    encodedData.get(outData);
                    try {
                        byte[] adtsHeader = new byte[7];
                        addADTStoPacket(adtsHeader, outData.length);
                        mOutputStream.write(adtsHeader, 0, 7);
                        mOutputStream.write(outData, 0, outData.length);
                    } catch( IOException e){
                        Log.e(TAG, e.getMessage());
                    }
                    */
                    mAudioMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                    Log.d(TAG, "AddData:" + String.valueOf(bufferInfo.offset + bufferInfo.size));
                }

                prevOutputPTSUs = bufferInfo.presentationTimeUs;
                mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;      // out of while
                }
            }
            Log.d(TAG, "drain out");
        }
    }
    public void release() {
        encoding = false;
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e){
        }
        if(mAudioMuxer != null){
            int stopCnt = 0;
            while(stopCnt < 10) {
                try {
                    mAudioMuxer.stop();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Muxer Stop Error");
                }
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e){
                }
                stopCnt++;

            }
            mAudioMuxer.release();
            mAudioMuxer = null;
        }
        if (mAudioEncoder != null) {
            try {
                mAudioEncoder.stop();
            } catch (Exception e) {
            }
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        if( mSocket != null){
            try {
                mSocket.close();
                mSocket = null;
            } catch( IOException e){
                Log.e(TAG, e.getMessage());
            }
        }
        Log.d(TAG, "release");
    }
    public Runnable conSocket = new Runnable() {
        public void run() {
            Log.d(TAG, "in");
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
                mSocket = new Socket("192.168.0.16", 9999);

                mFileDescriptor = ParcelFileDescriptor.fromSocket(mSocket);
                mOutputStream = new BufferedOutputStream(mSocket.getOutputStream());
                Log.d(TAG, "Connection Socket");
            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
            Log.d(TAG, "out");
        }
    };
    private long prevOutputPTSUs = 0;
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }
    /**
     *  Add ADTS header at the beginning of each and every AAC packet.
     *  This is needed as MediaCodec encoder generates a packet of raw
     *  AAC data.
     *
     *  Note the packetLen must count in the ADTS header itself.
     **/
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }
}
