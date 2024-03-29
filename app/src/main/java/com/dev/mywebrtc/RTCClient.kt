package com.dev.mywebrtc

import android.app.Application
import com.dev.mywebrtc.models.MessageModel
import com.dev.mywebrtc.util.MySdpObserver
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class RTCClient(
    private val application: Application,
    private val userName: String,
    private val socketRepository: SocketRepository,
    private val observer: PeerConnection.Observer
) {

    init {
        initPeerConnectionFactory(application)
    }

    private val eglBaseContext = EglBase.create()
    private val peerConnectionFactory by lazy {
        createPeerConnectionFactory()
    }
    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:iphone-stun.strato-iphone.de:3478")
            .createIceServer(),
        PeerConnection.IceServer("stun:stun.relay.metered.ca:80"),
        PeerConnection.IceServer(
            "turn:global.relay.metered.ca:80",
            "68fe1fd8a1b97eaae5107dfa",
            "LMeZeSRnfD8m/JQ1"
        ),
        PeerConnection.IceServer(
            "turn:global.relay.metered.ca:80?transport=tcp",
            "68fe1fd8a1b97eaae5107dfa",
            "LMeZeSRnfD8m/JQ1"
        ),
        PeerConnection.IceServer(
            "turn:global.relay.metered.ca:443",
            "68fe1fd8a1b97eaae5107dfa",
            "LMeZeSRnfD8m/JQ1"
        ),
        PeerConnection.IceServer(
            "turns:global.relay.metered.ca:443?transport=tcp",
            "68fe1fd8a1b97eaae5107dfa",
            "LMeZeSRnfD8m/JQ1"
        ),
    )

    private val peerConnection by lazy {
        createPeerConnection(observer)
    }

    private val localVideoSource by lazy {
        peerConnectionFactory.createVideoSource(false)
    }
    private val localAudioSource by lazy {
        peerConnectionFactory.createAudioSource(MediaConstraints())
    }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localStream: MediaStream? = null


    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(peerConnectionOption)
    }


    /*
   * create peer connection factory
   * */
    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBaseContext.eglBaseContext, true, true)
            ).setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBaseContext.eglBaseContext)
            ).setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }


    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }


    fun initializeSurfaceView(surfaceView: SurfaceViewRenderer) {
        surfaceView.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglBaseContext.eglBaseContext, null)
        }
    }


    fun startLocalVideo(surfaceView: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglBaseContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application)
        videoCapturer?.initialize(
            surfaceTextureHelper,
            surfaceView.context,
            localVideoSource.capturerObserver
        )
        videoCapturer?.startCapture(320, 240, 30)
        localVideoTrack =
            peerConnectionFactory.createVideoTrack("local_video_track", localVideoSource)
        localVideoTrack?.addSink(surfaceView)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource)
        localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream?.addTrack(localAudioTrack)
        localStream?.addTrack(localVideoTrack)

        peerConnection?.addStream(localStream)
    }


    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }
    }


    fun call(targetUserName: String) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )

                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                type = "create_offer",
                                name = userName,
                                target = targetUserName,
                                data = offer
                            )
                        )
                    }
                }, desc)
            }

        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection?.setRemoteDescription(object : MySdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }
        }, session)
    }


    fun answer(targetUserName: String?) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )

                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                type = "create_answer",
                                name = userName,
                                target = targetUserName,
                                data = answer
                            )
                        )
                    }
                }, desc)
            }

        }, mediaConstraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection?.addIceCandidate(p0)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleVideo(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall(userName: String, target: String) {
        videoCapturer?.dispose()
        localStream?.dispose()
        peerConnection?.close()
        socketRepository.sendMessageToSocket(
            MessageModel(
                type = "end_call",
                name = userName,
                target = target
            )
        )
    }
}