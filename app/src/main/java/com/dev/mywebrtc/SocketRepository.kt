package com.dev.mywebrtc

import android.util.Log
import com.dev.mywebrtc.models.MessageModel
import com.dev.mywebrtc.util.NewMessageInterface
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class SocketRepository(private val messageInterface: NewMessageInterface) {
    private val TAG = "SocketRepository"
    private var webSocket: WebSocketClient? = null
    private var username: String? = null
    private val gson = Gson()

    fun initSocket(username: String) {
        this.username = username

        // if you are using android emulator your local websocket address is going to be "ws://10.0.2.2:3000"
        // if you are using your phone as emulator your local address is going to be this : "ws://192.168.1.40:3000" => 'ws://your_system_IPv4_address:port_number
        // but if your websocket is deployed you can add your websocket address here



//        webSocket = object : WebSocketClient(URI("ws://10.0.2.2:3000")){
        webSocket = object : WebSocketClient(URI("ws://192.168.1.40:3000")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                sendMessageToSocket(
                    MessageModel(
                        "store_user", username, null, null
                    )
                )
            }

            override fun onMessage(message: String?) {
                try {
                    messageInterface.onNewMessage(gson.fromJson(message,MessageModel::class.java))
                }catch (e:Exception){
                    e.printStackTrace()
                }

            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "onClose: $code:- $reason")
            }

            override fun onError(ex: Exception?) {
                Log.d(TAG, "onError: $ex")
            }

        }

        webSocket?.connect()
    }

    fun closeSocketConnection(){
        webSocket?.close()
    }

    fun sendMessageToSocket(message: MessageModel) {
        try {
            Log.d(TAG, "sendMessageToSocket: $message")
            webSocket?.send(Gson().toJson(message))
        } catch (e: Exception) {
            Log.d(TAG, "sendMessageToSocket: $e")
            e.printStackTrace()
        }
    }
}