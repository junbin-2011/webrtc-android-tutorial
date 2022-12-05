package cc.rome753.wat;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

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
    private PeerConnectionFactory mPeerConnFact;
    private PeerConnection mPeerConn;
    private SurfaceViewRenderer mLocalView;
    private SurfaceViewRenderer mRemoteView;
    private MediaStream mMediaStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        Log.i(TAG, "create VideoSource with front VideoCapturer");
        SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        VideoCapturer videoCapturer = createCameraCapturer(false);
        VideoSource videoSource = mPeerConnFact.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(helper, getApplicationContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(480, 640, 30);

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
        mPeerConn = mPeerConnFact.createPeerConnection(iceServers, new PeerConnectionObserver(TAG + "-PeerConnObserver") {
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

    private VideoCapturer createCameraCapturer(boolean isFront) {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (isFront ? enumerator.isFrontFacing(deviceName) : enumerator.isBackFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
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
        mPeerConn.addIceCandidate(new IceCandidate(data.optString("id"), data.optInt("label"), data.optString("candidate")));
    }
}
