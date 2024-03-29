package com.dev.mywebrtc

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dev.mywebrtc.databinding.ActivityCallBinding
import com.dev.mywebrtc.models.IceCandidateModel
import com.dev.mywebrtc.models.MessageModel
import com.dev.mywebrtc.util.NewMessageInterface
import com.dev.mywebrtc.util.PeerConnectionObserver
import com.dev.mywebrtc.util.RTCAudioManager
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class CallActivity : AppCompatActivity(), NewMessageInterface {
    private val binding by lazy {
        ActivityCallBinding.inflate(layoutInflater)
    }
    private var userName: String? = null
    private var socketRepository: SocketRepository? = null
    private var rtcClient: RTCClient? = null
    private val TAG = CallActivity::class.java.name
    private var target: String = ""
    private val gson = Gson()
    private var isMute = false
    private var isCameraPause = false
    private val rtcAudioManager by lazy {
        RTCAudioManager.create(this)
    }
    private var isSpeakerMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initBlock()
    }

    private fun initBlock() {
        userName = intent.getStringExtra("username")
        socketRepository = SocketRepository(this)
        userName?.let { socketRepository?.initSocket(it) }

        rtcClient = RTCClient(application, userName!!, socketRepository!!, object :
            PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                rtcClient?.addIceCandidate(p0)

                val candidate = hashMapOf(
                    "sdpMid" to p0?.sdpMid,
                    "sdpMLineIndex" to p0?.sdpMLineIndex,
                    "sdpCandidate" to p0?.sdp
                )

                socketRepository?.sendMessageToSocket(
                    MessageModel("ice_candidate", userName, target, candidate)
                )
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)

                p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
            }

        })

        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        binding.apply {
            callBtn.setOnClickListener {
                socketRepository?.sendMessageToSocket(
                    MessageModel(
                        "start_call", userName, targetUserNameEt.text.toString(), null
                    )
                )
                target = targetUserNameEt.text.toString()
            }

            switchCameraButton.setOnClickListener {
                rtcClient?.switchCamera()
            }

            micButton.setOnClickListener {
                if (isMute) {
                    isMute = false
                    micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
                } else {
                    isMute = true
                    micButton.setImageResource(R.drawable.ic_baseline_mic_24)
                }
                rtcClient?.toggleAudio(isMute)
            }


            videoButton.setOnClickListener {
                if (isCameraPause) {
                    isCameraPause = false
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                } else {
                    isCameraPause = true
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                }

                rtcClient?.toggleVideo(isCameraPause)
            }

            audioOutputButton.setOnClickListener {
                if (isSpeakerMode) {
                    isSpeakerMode = false
                    audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
                } else {
                    isSpeakerMode = true
                    audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
                }

            }

            endCallButton.setOnClickListener {
                rtcClient?.endCall(userName!!,target!!)

                setCallLayoutGone()
                setWhoToCallLayoutVisible()
                setInComingCallLayoutGone()
                Toast.makeText(this@CallActivity,"Send call end",Toast.LENGTH_SHORT).show()
                binding.localView.release()
                binding.remoteView.release()
            }

        }


    }

    override fun onNewMessage(message: MessageModel) {
        Log.d(TAG, "onNewMessage $message")
        when (message.type) {
            "call_response" -> {
                if (message.data == "user is not online") {
                    // user is not reachable
                    runOnUiThread {
                        Toast.makeText(
                            this@CallActivity,
                            "user is not reachable",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // we are ready for call

                    runOnUiThread {
                        setWhoToCallLayoutGone()
                        setCallLayoutVisible()
                        binding.apply {
                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.initializeSurfaceView(remoteView)
                            rtcClient?.startLocalVideo(localView)
                            rtcClient?.call(targetUserNameEt.text.toString())
                        }
                    }
                }
            }

            "offer_received" -> {
                runOnUiThread {
                    setInComingCallLayoutVisible()
                    binding.apply {
                        incomingNameTV.text = "${message.name.toString()} is calling you"

                        rejectButton.setOnClickListener {
                            setInComingCallLayoutGone()
                        }

                        acceptButton.setOnClickListener {
                            setInComingCallLayoutGone()
                            setCallLayoutVisible()
                            setWhoToCallLayoutGone()

                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.initializeSurfaceView(remoteView)
                            rtcClient?.startLocalVideo(localView)

                            val session = SessionDescription(
                                SessionDescription.Type.OFFER,
                                message.data.toString()
                            )
                            rtcClient?.onRemoteSessionReceived(session)
                            rtcClient?.answer(message.name!!)
                            target = message.name!!
                            binding.remoteViewLoading.visibility = View.GONE
                        }
                    }
                }
            }

            "answer_received" -> {

                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString()
                )
                rtcClient?.onRemoteSessionReceived(session)
                runOnUiThread { binding.remoteViewLoading.visibility = View.GONE }

            }

            "ice_candidate" -> {
                runOnUiThread {
                    try {
                        val receivingCandidate =
                            gson.fromJson(gson.toJson(message.data), IceCandidateModel::class.java)
                        rtcClient?.addIceCandidate(
                            IceCandidate(
                                receivingCandidate.sdpMid,
                                Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),
                                receivingCandidate.sdpCandidate
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            "end_call" -> {
                runOnUiThread {
                    setCallLayoutGone()
                    setWhoToCallLayoutVisible()
                    setInComingCallLayoutGone()
                    binding.localView.release()
                    binding.remoteView.release()
                    Toast.makeText(this@CallActivity,"Call ended by ${message.name}",Toast.LENGTH_SHORT).show()
                    socketRepository?.closeSocketConnection()
//                    finish()
                }
            }

        }
    }

    private fun setInComingCallLayoutGone() {
        binding.incomingCallLayout.visibility = View.GONE
    }

    private fun setInComingCallLayoutVisible() {
        binding.incomingCallLayout.visibility = View.VISIBLE
    }

    private fun setCallLayoutGone() {
        binding.callLayout.visibility = View.GONE
    }

    private fun setCallLayoutVisible() {
        binding.callLayout.visibility = View.VISIBLE
    }

    private fun setWhoToCallLayoutGone() {
        binding.whoToCallLayout.visibility = View.GONE
    }

    private fun setWhoToCallLayoutVisible() {
        binding.whoToCallLayout.visibility = View.VISIBLE
    }
}