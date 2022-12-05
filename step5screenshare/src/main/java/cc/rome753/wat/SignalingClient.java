package cc.rome753.wat;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Created by chao on 2019/1/30.
 */

public class SignalingClient {

    private static final String TAG = MainActivity.TAG + "-" + SignalingClient.class.getSimpleName();
    private static SignalingClient instance;

    public static SignalingClient get() {
        if (instance == null) {
            synchronized (SignalingClient.class) {
                if (instance == null) {
                    instance = new SignalingClient();
                }
            }
        }
        return instance;
    }


    private Socket mSocket;
    private String mRoom = "OldPlace";
    private Callback mCallback;

    private final TrustManager[] mTrustAll = new TrustManager[]{new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            Log.i(TAG, "TrustManager.checkClientTrusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            Log.i(TAG, "TrustManager.checkServerTrusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            Log.i(TAG, "TrustManager.getAcceptedIssuers");
            return new X509Certificate[0];
        }
    }};

    private SignalingClient() {
        init();
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    private void init() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, mTrustAll, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslContext);

            String ipAddr = "https://192.168.0.103:8080";
            Log.w(TAG, "connect >>> " + ipAddr);
            mSocket = IO.socket(ipAddr);
            mSocket.connect();

            Log.w(TAG, "emit>>>create or join");
            mSocket.emit("create or join", mRoom);

            mSocket.on("created", args -> {
                Log.i(TAG, "on【created】");
                mCallback.onCreateRoom();
            });
            mSocket.on("full", args -> {
                Log.i(TAG, "on【full】");
            });
            mSocket.on("join", args -> {
                Log.i(TAG, "on【join】");
                mCallback.onPeerJoined();
            });
            mSocket.on("joined", args -> {
                Log.i(TAG, "on【joined】");
                mCallback.onSelfJoined();
            });
            mSocket.on("log", args -> {
                Log.i(TAG, "on【log】, args=" + Arrays.toString(args));
            });
            mSocket.on("bye", args -> {
                Log.i(TAG, "on【bye】, args0=" + args[0]);
                mCallback.onPeerLeave((String) args[0]);
            });
            mSocket.on("message", args -> {
                Log.i(TAG, "on【message】, args=" + Arrays.toString(args));
                Object arg = args[0];
                if (arg instanceof String) {

                } else if (arg instanceof JSONObject) {
                    JSONObject data = (JSONObject) arg;
                    String type = data.optString("type");
                    if ("offer".equals(type)) {
                        mCallback.onOfferReceived(data);
                    } else if ("answer".equals(type)) {
                        mCallback.onAnswerReceived(data);
                    } else if ("candidate".equals(type)) {
                        mCallback.onIceCandidateReceived(data);
                    }
                }
            });
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void sendIceCandidate(IceCandidate iceCandidate) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("type", "candidate");
            jo.put("label", iceCandidate.sdpMLineIndex);
            jo.put("id", iceCandidate.sdpMid);
            jo.put("candidate", iceCandidate.sdp);
            Log.w(TAG, "emit>>>message[IceCandidate], args=" + jo);
            mSocket.emit("message", jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendSessionDescription(SessionDescription sdp) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("type", sdp.type.canonicalForm());
            jo.put("sdp", sdp.description);
            Log.w(TAG, "emit>>>message[SDP], args=" + jo);
            mSocket.emit("message", jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public interface Callback {

        void onCreateRoom();

        void onPeerJoined();

        void onSelfJoined();

        void onPeerLeave(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);
    }

}
