package cn.entertech.biomoduledemo

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class WebSocketManager() {
    var mBrainDataWebSocket: WebSocketClient? = null
//    测试服
    var url: URI = URI("ws://test.affectivecloud.com:8080")
//    正式服
//    var url: URI = URI("ws://api.affectivecloud.com:8080")
    var brainDataCallback = mutableListOf<(String?) -> Unit>()

    companion object {
        private var socketManager: WebSocketManager? = null
        fun getInstance(): WebSocketManager {
            if (socketManager == null) {
                synchronized(WebSocketManager::class.java) {
                    if (socketManager == null) {
                        socketManager = WebSocketManager()
                    }
                }
            }
            return socketManager!!
        }
    }

    fun connect(connectedCallback: ((ServerHandshake?) -> Unit)?) {
        try {
            mBrainDataWebSocket = object : WebSocketClient(url, Draft_6455(), null, 100000) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.d("WebSocketManager", "onConnected " + handshakedata.toString())
                    connectedCallback?.invoke(handshakedata)
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {

                    Log.d("WebSocketManager", "onClose :" + code + "::reason is " + reason)
                }

                override fun onMessage(message: String?) {
                    Log.d("WebSocketManager", "receive msg is " + message)
                    brainDataCallback.forEach {
                        it.invoke(message)
                    }
                }

                override fun onError(ex: java.lang.Exception?) {
                    Log.d("WebSocketManager", "onError " + ex.toString())
                }
            }
            mBrainDataWebSocket!!.connect()
        } catch (e: Exception) {

        }
    }


    fun connect() {
        connect(null)
    }


    fun sendMessage(data: String) {
        mBrainDataWebSocket?.send(data + "\r\n")
    }


    fun addBrainDataListener(callback: ((String?) -> Unit)) {
        brainDataCallback.add(callback)
    }

    fun removeBrainDataListener(callback: ((String) -> Unit)) {
        brainDataCallback.remove(callback)
    }


    fun close() {
        mBrainDataWebSocket?.close()

    }

}