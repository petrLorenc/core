package com.promethist.client.common

import com.promethist.client.BotEvent
import com.promethist.client.BotSocket
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class OkHttp3BotClientSocket(url: String, raiseExceptions: Boolean = false, socketPing: Long = 10):
        BotClientSocket(url, raiseExceptions, socketPing) {

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            state = BotSocket.State.Open
            listener?.onOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = objectMapper.readValue(text, BotEvent::class.java)
            listener?.onEvent(event)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            logger.debug("onMessage(bytes[${bytes.size}])")
            listener?.onAudioData(bytes.toByteArray())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.info("onClosing(webSocket = $webSocket, code = $code, reason = $reason)")
            state = BotSocket.State.Closing
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.info("onClosed(webSocket = $webSocket, code = $code, reason = $reason)")
            state = BotSocket.State.Closed
            listener?.onClose()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.info("onFailure(webSocket = $webSocket, t = $t, reponse = $response)")
            state = BotSocket.State.Failed
            listener?.onFailure(t)
        }
    }

    private var socket: WebSocket? = null

    override fun open() {
        val url = url.replace("http", "ws") + DEFAULT_URI
        logger.info("open() {url = $url}")
        val request = Request.Builder().url(url).build()
        val socketBuilder = OkHttpClient.Builder()
        if (socketPing > 0)
            socketBuilder.pingInterval(socketPing, TimeUnit.SECONDS)
        socket = socketBuilder.build().newWebSocket(request, socketListener)
    }

    override fun close() {
        super.close()
        socket!!.close(1000, "CLIENT_CLOSE")
    }


    override fun sendText(text: String) {
        socket!!.send(text)
    }

    override fun sendBytes(bytes: ByteBuffer) {
        socket!!.send(bytes.toByteString())
    }
}