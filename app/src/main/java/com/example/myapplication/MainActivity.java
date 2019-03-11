package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE
    };

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private SurfaceHolder.Callback surfaceListener = new SurfaceHolder.Callback() {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            //camera.release();
            Log.e("surface", "Destroyed");
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            //// TODO Auto-generated method stub
            //camera = Camera.open();
            //try {
            //   camera.setPreviewDisplay(holder);
            //} catch (IOException e) {
            //    // TODO Auto-generated catch block
            //    e.printStackTrace();
            //}
            Log.e("surface", "Created");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            //// TODO Auto-generated method stub
            //Camera.Parameters parameters = camera.getParameters();
            //parameters.setPreviewSize(width, height);
            //camera.startPreview();
            Log.e("surface", "Changed");
        }
    };

    private SurfaceView mPreview = null;
    private SurfaceHolder mHolder = null;
    String Path = "";
    Camera mCamera = null;
    Socket mSocket = null;
    DataOutputStream mOS = null;
    int mWidth = 0;
    int mHeight = 0;
    String TAG = "MyApp";
    AvcEncoder mEncoder = null;

    private void AppInfo() {
        Log.i(TAG, "BOARD = " + Build.BOARD);
        Log.i(TAG, "BRAND = " + Build.BRAND);
        Log.i(TAG, "CPU_ABI = " + Build.CPU_ABI);
        Log.i(TAG, "DEVICE = " + Build.DEVICE);
        Log.i(TAG, "DISPLAY = " + Build.DISPLAY);
        Log.i(TAG, "FINGERPRINT = " + Build.FINGERPRINT);
        Log.i(TAG, "HOST = " + Build.HOST);
        Log.i(TAG, "ID = " + Build.ID);
        Log.i(TAG, "MANUFACTURER = " + Build.MANUFACTURER);
        Log.i(TAG, "MODEL = " + Build.MODEL);
        Log.i(TAG, "PRODUCT = " + Build.PRODUCT);
        Log.i(TAG, "TAGS = " + Build.TAGS);
        Log.i(TAG, "TYPE = " + Build.TYPE);
        Log.i(TAG, "USER = " + Build.USER);
        Log.i(TAG, "VERSION.RELEASE = " + Build.VERSION.RELEASE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 화면을 portrait(세로) 화면으로 고정하고 싶은 경우
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // 화면을 landscape(가로) 화면으로 고정하고 싶은 경우

        //setContentView(R.layout.main);
        // setContentView()가 호출되기 전에 setRequestedOrientation()이 호출되어야 함

        Log.e("Main", "onCreate");
        AppInfo();
        verifyPermissions(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (mPreview == null) {
            mPreview = findViewById(R.id.surface);
            //mPreview.getHolder().setFixedSize(740, 360);
            mHolder = mPreview.getHolder();
            //mHolder.addCallback(this);
            mHolder.addCallback(surfaceListener);
        }

        String sd = Environment.getExternalStorageDirectory().getAbsolutePath();
        Path = sd + "/recvideo.mp4";


        Button fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCamera == null) {
                    Thread conThread = new Thread(conSocket);
                    conThread.start();

                    mPreview.setVisibility(View.VISIBLE);
                    //Thread conThread = new Thread(null, conSocket, "conSocket");
                    //conThread.start();
                    mCamera = Camera.open(0);
                    Camera.Parameters parameters = mCamera.getParameters();
                    Log.d("PreviewFormat :", String.valueOf(parameters.getPreviewFormat()));
                    Log.d("PreviewFrameRate:", String.valueOf(parameters.getPreviewFrameRate()));
                    Log.d("PreviewWidth:", String.valueOf(parameters.getPreviewSize().width));
                    Log.d("PreviewHeight:", String.valueOf(parameters.getPreviewSize().height));
                    parameters.setPreviewFormat(ImageFormat.YV12);
                    parameters.setPreviewFrameRate(30);
                    try {
                        mCamera.setPreviewDisplay(mHolder);
                        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
                        mWidth = parameters.getPreviewSize().width;
                        mHeight = parameters.getPreviewSize().height;
                        if (!Build.PRODUCT.equals("sdk_gphone_x86")) {
                            mWidth = 640;
                            mHeight = 360;
                        }
                        int tmp = mWidth;
                        mCamera.setDisplayOrientation(0);
                        parameters.setPreviewSize(mWidth, mHeight);
                    } catch (IOException e) {
                        Log.e("Camera", e.getMessage());
                    }
                    mEncoder = new AvcEncoder(mSocket, mWidth, mHeight);
                    mCamera.setParameters(parameters);
                    mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                        private long timestamp = 0;

                        public synchronized void onPreviewFrame(byte[] data, Camera camera) {
                            Log.v("CameraTest", "Time Gap = " + (System.currentTimeMillis() - timestamp) + " : size = " + String.valueOf(data.length));
                            timestamp = System.currentTimeMillis();
                            //Thread sendThread = new Thread(sendMsg);
                            //encode(data);
                            //Thread sendThread = new Thread(new sendMessage(mOS, data));
                            //sendThread.start();
                            byte[] Data = YV12toYUV420PackedSemiPlanar(data, mWidth, mHeight);
                            //mEncoder.offerEncoder(Data);
                            mEncoder.putData(Data);
                            Log.v("CameraTest", "Time Gap = " + (System.currentTimeMillis() - timestamp) + " : size = " + String.valueOf(Data.length));

                        }
                    });
                    //initCodec();
                    mCamera.startPreview();
                    //mCamera.unlock();
                } else {
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (success) {
                                Log.d("Camera", "autoFocus Success");
                            }
                            Log.d("Camera", "autoFocus");
                        }

                    });

                }
            }
        });
        Button fab2 = findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCamera != null) {
                    Log.i("Media", "stop camera");
                    mCamera.stopPreview();
                    mCamera.setPreviewCallback(null);
                    mCamera.release();
                    mCamera = null;
                }
                if (mEncoder != null) {
                    mEncoder.close();
                    mEncoder = null;
                }
                //Thread stopThread = new Thread(stopRecorde);
                //Log.i("Media", "stop Thread Created");
                //stopThread.start();
                Log.d("fab2", "stop");

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        Log.e("Main", "onDestroy in");
        super.onDestroy();
        if (mCamera != null) {
            Log.i("Media", "stop camera");
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
        Log.e("Main", "onDestroy out");
    }

    public Runnable conSocket = new Runnable() {
        public void run() {
            Log.d("conSocket", "in");
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
                mSocket = new Socket("192.168.0.16", 8888);

                //mFileDescriptor = ParcelFileDescriptor.fromSocket(mSocket);
                mOS = new DataOutputStream(mSocket.getOutputStream());
                Log.d("start", "Connection Socket");
                //mSocket.close();
            } catch (IOException e) {
                Log.d("start", e.getMessage());
            }
            Log.d("conSocket", "out");
        }
    };

    class sendMessage implements Runnable {
        byte[] mData;
        DataOutputStream mDos;

        sendMessage(DataOutputStream dos, byte[] data) {
            mDos = dos;
            //mData = data;
            mData = YV12toYUV420PackedSemiPlanar(data, mWidth, mHeight);
            //mData = YV12toYUV420Planar(data, mWidth, mHeight);
            //mData = convertYUV420_NV21toRGB8888(data, mWidth, mHeight);
        }

        public void run() {
            Log.d("sendMsg", "in");
            //encode(mData);
            mEncoder.offerEncoder(mData);
            /*
            try {
                mDos.write(mData);
                //mDos.writeUTF("This is the first type of message.");
                mDos.flush();
                Log.d("start", "sendMsg  Socket");
            } catch (IOException e) {
                Log.d("start", e.getMessage());
            }
            */
            Log.d("sendMsg", "out");
        }
    }

    public byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width * height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width * height; i < width * height + (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i + (width / 2 * height / 2)];
        for (int i = width * height + (width / 2 * height / 2); i < width * height + 2 * (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i - (width / 2 * height / 2)];
        return i420bytes;
    }

    public static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        byte[] output = new byte[input.length];
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    public static byte[] YV12toYUV420Planar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        byte[] output = new byte[input.length];
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

    public class AvcEncoder {

        private MediaCodec mediaCodec;
        private BufferedOutputStream outputStream;
        private int mWidth, mHeight;
        private Socket mSocket;
        private boolean isDoEncoding;
        private boolean isClose;
        ByteBuffer[] inputBuffers;

        public AvcEncoder(Socket socket, int width, int height) {
            mWidth = width;
            mHeight = height;
            mSocket = socket;
            isDoEncoding = false;
            isClose = false;
            File f = new File(Environment.getExternalStorageDirectory(), "video_encoded.264.mp4");
            //touch(f);
            Log.d("AvcEncoder", "in");
            try {
                outputStream = new BufferedOutputStream(mSocket.getOutputStream());
                //outputStream = new BufferedOutputStream(new FileOutputStream(f));
                Log.i("AvcEncoder", "outputStream initialized");
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("start", e.getMessage());
            }
            Log.d("AvcEncoder", "ConOK");

            try {
                mediaCodec = MediaCodec.createEncoderByType("video/avc");
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 25);
                mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread doEncodeThread = new Thread(doEncode);
            doEncodeThread.start();
            inputBuffers = mediaCodec.getInputBuffers();
            Log.d("AvcEncoder", "out");
        }

        public void close() {
            try {
                isClose = true;
                while (!isDoEncoding) {
                    Thread.sleep(100);
                    Log.d("Encoder", "Wait Stop");
                }
                mediaCodec.stop();
                mediaCodec.release();
                outputStream.flush();
                outputStream.close();
                mSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void putData(byte[] input) {
            try {
                //ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(1000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    //inputBuffer.clear();
                    inputBuffer.put(input);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }

        }

        public Runnable doEncode = new Runnable() {
            public void run() {
                try {
                    isDoEncoding = true;
                    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = -1;
                    //byte[] outData = new byte[100000];
                    while (!isClose) {
                        do {
                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
                            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                //Log.e("enc: out", "INFO_TRY_AGAIN_LATER: " );
                                    break;
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat format = mediaCodec.getOutputFormat();
                                Log.e("enc: out: ", " INFO_OUTPUT_FORMAT_CHANGED: " + format.toString());
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                outputBuffers = mediaCodec.getOutputBuffers();
                                Log.e("enc: out:", " INFO_OUTPUT_BUFFERS_CHANGED");
                            } else {
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                outputStream.write(outData, 0, outData.length);
                                //outputStream.write(outData, 0, bufferInfo.size);
                                Log.i("AvcEncoder", bufferInfo.size + " bytes written : INDX :" + String.valueOf(outputBufferIndex));

                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    Log.e("enc: out:", " EOS");
                                    break;
                                }
                            }
                            /*
                            if (outputBufferIndex >= 0) {
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                outputStream.write(outData, 0, outData.length);
                                Log.i("AvcEncoder", outData.length + " bytes written");

                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            }
                            */
                        } while (outputBufferIndex >= 0);
                        //Thread.sleep(100);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                isDoEncoding = false;
            }
        };

        public void offerEncoder(byte[] input) {
            try {
                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(input);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    outputStream.write(outData, 0, outData.length);
                    Log.i("AvcEncoder", outData.length + " bytes written");

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

        }
    }
}

