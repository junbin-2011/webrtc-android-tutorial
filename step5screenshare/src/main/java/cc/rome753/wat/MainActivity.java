package cc.rome753.wat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SignalingClient.Callback {

    public static final String TAG = "Frank";
    public static final int CODE_PERMISSION = 1000;
    public static final int CODE_PROJECTION = 100;
    private Intent mScreenCaptInt;
    private PeerConnectionFactory mPeerConnFact;
    private PeerConnection mPeerConn;
    private SurfaceViewRenderer mLocalView;
    private SurfaceViewRenderer mRemoteView;
    private MediaStream mMediaStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
    }

    /**
     * 检测各种权限
     * @return
     */
    private void checkPermissions() {
        List<String> neededPermissions = new ArrayList<>();
        if (!checkSelfPermissionGranted(Manifest.permission.CAMERA)) {
            neededPermissions.add(Manifest.permission.CAMERA);
        }
        if (!checkSelfPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!checkSelfPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!neededPermissions.isEmpty()) {
            String[] permissions = neededPermissions.toArray(new String[neededPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, CODE_PERMISSION);
        } else {
            requestCapScreen();
        }
    }

    private boolean checkSelfPermissionGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CODE_PERMISSION) {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "您拒绝了权限相关功能将无法使用！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                requestCapScreen();
            } else {
                Toast.makeText(MainActivity.this, "发生未知错误！", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "对不起，您拒绝了权限无法使用此功能！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {
                Toast.makeText(MainActivity.this, "发生未知错误！", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestCapScreen() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), CODE_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_PROJECTION) {
            mScreenCaptInt = data;
            create();
        } else {
            Toast.makeText(this, "unknow request code: " + requestCode, Toast.LENGTH_SHORT);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "permission denied !", Toast.LENGTH_SHORT);
            return;
        }
    }

    private void create() {
        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();

        // create PeerConnectionFactory
        Log.i(TAG, "create PeerConnectionFactory");
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory encoderFact = new DefaultVideoEncoderFactory(eglBaseContext, true, true);
        DefaultVideoDecoderFactory decoderFact = new DefaultVideoDecoderFactory(eglBaseContext);
        mPeerConnFact = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFact)
                .setVideoDecoderFactory(decoderFact)
                .createPeerConnectionFactory();

        // create VideoSource with front VideoCapturer
        Log.i(TAG, "create VideoSource with ScreenCapture");
        SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        VideoCapturer screenCapturer = new ScreenCapturerAndroid(mScreenCaptInt, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
            }
        });;
        VideoSource videoSource = mPeerConnFact.createVideoSource(screenCapturer.isScreencast());
        screenCapturer.initialize(helper, getApplicationContext(), videoSource.getCapturerObserver());
        screenCapturer.startCapture(480, 640, 30);

        // Front Video SurfaceView
        mLocalView = findViewById(R.id.localView);
        mLocalView.setMirror(true);
        mLocalView.init(eglBaseContext, null);

        // create VideoTrack addSink LocalView
        Log.i(TAG, "create VideoTrack addSink LocalView");
        VideoTrack videoTrack = mPeerConnFact.createVideoTrack("100", videoSource);
        videoTrack.addSink(mLocalView);


        // Remote SurfaceView
        mRemoteView = findViewById(R.id.remoteView);
        mRemoteView.setMirror(false);
        mRemoteView.init(eglBaseContext, null);

        AudioSource audioSource = mPeerConnFact.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = mPeerConnFact.createAudioTrack("101", audioSource);

        // Local media stream add video/audio
        Log.i(TAG, "create Local media stream add video/audio");
        mMediaStream = mPeerConnFact.createLocalMediaStream("mediaStream");
        mMediaStream.addTrack(videoTrack);
        mMediaStream.addTrack(audioTrack);

        SignalingClient.get().setCallback(this);
        call();
    }


    private void call() {
        // ICE Server
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        Log.i(TAG, "createPeerConnection with IceServers");
        mPeerConn = mPeerConnFact.createPeerConnection(iceServers,
                new PeerConnectionObserver(TAG + "-PeerConnObserver") {
                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        super.onIceCandidate(iceCandidate);
                        SignalingClient.get().sendIceCandidate(iceCandidate);
                    }

                    @Override
                    public void onAddStream(MediaStream remoteMediaStream) {
                        super.onAddStream(remoteMediaStream);
                        Log.i(TAG, "create VideoTrack addSink RemoteView");
                        VideoTrack remoteVideoTrack = remoteMediaStream.videoTracks.get(0);
                        runOnUiThread(() -> {
                            remoteVideoTrack.addSink(mRemoteView);
                        });
                    }
                });
        Log.i(TAG, "PeerConnection add local media stream");
        mPeerConn.addStream(mMediaStream);
    }

    @Override
    public void onCreateRoom() {
        Log.i(TAG + "-Signaling", "onCreateRoom");
    }

    @Override
    public void onPeerJoined() {
        Log.i(TAG + "-Signaling", "onPeerJoined");
    }

    @Override
    public void onSelfJoined() {
        Log.i(TAG + "-Signaling", "onSelfJoined, then create offer");
        mPeerConn.createOffer(new SDPObserver(TAG + "-createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                super.onCreateSuccess(sdp);
                mPeerConn.setLocalDescription(new SDPObserver(TAG + "-setLocalDesc"), sdp);
                SignalingClient.get().sendSessionDescription(sdp);
            }
        }, new MediaConstraints());
    }

    @Override
    public void onPeerLeave(String msg) {
        Log.w(TAG + "-Signaling", "onPeerLeave >>> " + msg);
    }

    @Override
    public void onOfferReceived(JSONObject data) {
        Log.i(TAG + "-Signaling", "onOfferReceived >>> " + data);
        runOnUiThread(() -> {
            mPeerConn.setRemoteDescription(new SDPObserver(TAG + "-setRemoteDesc"),
                    new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")));
            mPeerConn.createAnswer(new SDPObserver(TAG + "-createAnswer") {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    super.onCreateSuccess(sdp);
                    mPeerConn.setLocalDescription(new SDPObserver(TAG + "-setLocalDesc"), sdp);
                    SignalingClient.get().sendSessionDescription(sdp);
                }
            }, new MediaConstraints());

        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        Log.i(TAG + "-Signaling", "onAnswerReceived >>> " + data);
        mPeerConn.setRemoteDescription(new SDPObserver(TAG + "-setRemoteDesc"),
                new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")));
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        Log.i(TAG + "-Signaling", "onIceCandidateReceived >>> " + data);
        mPeerConn.addIceCandidate(
                new IceCandidate(data.optString("id"), data.optInt("label"), data.optString("candidate")));
    }
}
