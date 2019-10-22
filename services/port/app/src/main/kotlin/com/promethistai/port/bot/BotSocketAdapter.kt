package com.promethistai.port.bot

import com.promethistai.common.ObjectUtil
import com.promethistai.port.DataService
import com.promethistai.port.model.Message
import com.promethistai.port.stt.*
import com.promethistai.port.tts.TtsConfig
import com.promethistai.port.tts.TtsRequest
import com.promethistai.util.Converter
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.thread

class BotSocketAdapter : BotSocket, WebSocketAdapter() {

    inner class BotSttCallback(private val event: BotEvent) : SttCallback {

        override fun onResponse(transcript: String, confidence: Float, final: Boolean) {
            try {
                if (final && !inputAudioStreamCancelled) {
                    if ((event.appKey != null) && dataService.getContract(event.appKey!!).sttAudioSave) {
                        val pcmData = ByteArray(sttBuffer.position())
                        sttBuffer.get(pcmData, 0, pcmData.size)
                        thread(start = true) {
                            //conversion of PCM to WAV
                            val wavData = Converter.pcmToWav(pcmData)
                            dataService.addCacheItemWithFile(event.message!!._id!!, "stt", "", wavData)
                        }
                    }
                    sendEvent(BotEvent(BotEvent.Type.Recognized, Message(items = mutableListOf(Message.Item(text = transcript)))))
                    onMessageEvent(event.apply {
                        this.message!!.items = mutableListOf(Message.Item(text = transcript, confidence = confidence.toDouble()))
                    })
                }
            } catch (e: IOException) {
                e.printStackTrace() }
        }

        override fun onError(e: Throwable) {
            e.printStackTrace()
            if (isConnected)
                sendEvent(BotEvent(BotEvent.Type.Error, Message(sender= "google stt",items = mutableListOf(Message.Item(text = e.message?:"")))))
        }

        override fun onOpen() {
            sendEvent(BotEvent(BotEvent.Type.InputAudioStreamOpen))
        }
    }

    private var logger = LoggerFactory.getLogger(BotSocketAdapter::class.qualifiedName)

    @Inject
    lateinit var botService: BotService

    @Inject
    lateinit var dataService: DataService

    private val objectMapper = ObjectUtil.defaultMapper
    private var sttService: SttService? = null
    private var sttStream: SttStream? = null
    private var clientRequirements: BotClientRequirements = BotClientRequirements()
    private var inputAudioStreamCancelled: Boolean = false
    private var speechProvider: String = "google"
    private var expectedPhrases: List<Message.ExpectedPhrase> = listOf()
    private val timer: Timer = Timer()
    private val timerTasks = mutableMapOf<String, TimerTask>()
    private val sttBuffer = ByteBuffer.allocate(1024 * 1024 * 32) // 32M limit (cca 5min @ 44.1kHz/16bit/stereo?)

    override fun onWebSocketBinary(payload: ByteArray, offset: Int, len: Int) {
        logger.info("onWebSocketBinary(payload = ByteArray(${payload.size}), offset = $offset, len = $len)")
        super.onWebSocketBinary(payload, offset, len)
        sttBuffer.put(payload)
        sttStream?.write(payload, offset, len)
    }

    /**
     * Determine if the response from botService will be followed by waiting for user input or another message will be sent to botService
     */
    fun onMessageEvent(event: BotEvent) {
        val response = botService.message(event.appKey!!, event.message!!)
        if (response != null) {
            expectedPhrases = response.expectedPhrases?: listOf()
            if (response.sessionEnded) {
                sendMessage(event.appKey!!, response)
                sendEvent(BotEvent(BotEvent.Type.SessionEnded))
                close(false)
            }
            // todo will not work correctly before the subdialogs in helena will be implemented
            else {
                sendMessage(event.appKey!!, response) // client will wait for user input
            }
        }
    }

    override fun onWebSocketText(json: String?) {
        super.onWebSocketText(json)
        try {
            val event = objectMapper.readValue(json, BotEvent::class.java)
            if (/*event == null || */event.type == null)
                return

            logger.info("onWebSocketText(event = $event)")

            if (event.message != null) {

                // set session id
                if (event.message!!.sessionId.isNullOrBlank()) {
                    event.message!!.sessionId = Message.createId()
                    sendEvent(BotEvent(BotEvent.Type.SessionStarted, Message(sessionId = event.message!!.sessionId)))
                }

                if (event.appKey != null && event.message!!.sender != null) {

                    val timerTaskKey = "${event.appKey}/${event.message!!.sender}"
                    if (!timerTasks.containsKey(timerTaskKey)) {
                        val timerTask = object : TimerTask() {
                            override fun run() {
                                val messages = dataService.popMessages(event.appKey!!, event.message!!.sender!!, 1)
                                for (message in messages)
                                    sendMessage(event.appKey!!, message)
                            }
                        }
                        timer.schedule(timerTask, 2000, 2000)
                        timerTasks.put(timerTaskKey, timerTask)
                    }
                }
            }
            onEvent(event)
        } catch (e: Exception) {
            e.printStackTrace()
            sendEvent(BotEvent(BotEvent.Type.Error, Message(sender = "port", items = mutableListOf(Message.Item(text = e.message?:"")))))
        }
    }

    override fun onEvent(event: BotEvent) =
        when (event.type) {

            BotEvent.Type.Requirements -> {
                clientRequirements = event.requirements?:BotClientRequirements()
                sendEvent(BotEvent(BotEvent.Type.Requirements, requirements = clientRequirements))
            }

            BotEvent.Type.SessionStarted ->
                sendEvent(BotEvent(BotEvent.Type.SessionStarted,
                        Message(sessionId = event.message?.sessionId?:Message.createId())))

            BotEvent.Type.SessionEnded -> sendEvent(BotEvent(BotEvent.Type.SessionEnded))

            BotEvent.Type.Message -> onMessageEvent(event)

            BotEvent.Type.InputAudioStreamOpen -> {
                close(false)
                val contract = dataService.getContract(event.appKey!!)
                val language = event.message?.language?.language?:contract.language
                val sttConfig = SttConfig(language, clientRequirements.sttSampleRate)
                sttService = SttServiceFactory.create(speechProvider, sttConfig, this.expectedPhrases, BotSttCallback(event))
                sttBuffer.rewind()
                sttStream = sttService?.createStream()
            }

            BotEvent.Type.InputAudioStreamClose -> close(false)

            BotEvent.Type.InputAudioStreamCancel -> close(true)

            else -> {}
        }


    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        close( false)
        timer.cancel()
    }

    override fun onWebSocketError(cause: Throwable?) {
        super.onWebSocketError(cause)
        close(false)
        timer.cancel()
    }

    private fun close(wasCancelled: Boolean) {
        this.inputAudioStreamCancelled = wasCancelled
        sttStream?.close()
        sttStream = null
        sttService?.close()
        sttService = null
    }

    @Synchronized
    @Throws(IOException::class)
    override fun sendEvent(event: BotEvent) {
        logger.info("sendEvent(event = $event)")
        remote.sendString(objectMapper.writeValueAsString(event))
    }

    fun sendBinaryData(data: ByteArray) {
        logger.info("sendBinaryData(data = ByteArray(${data.size}))")
        remote.sendBytes(ByteBuffer.wrap(data))
    }

    @Throws(IOException::class)
    internal fun sendMessage(appKey: String, message: Message) {
        val contract = dataService.getContract(appKey)
        message.expectedPhrases = null
        for (item in message.items) {
            if (item.text.isNullOrBlank()) {
                logger.debug("item.text.isNullOrBlank() == true")
            } else {
                val ttsRequest = TtsRequest()
                if (item.ssml != null) {
                    ttsRequest.text = item.ssml
                    ttsRequest.isSsml = true
                } else {
                    ttsRequest.text = item.text
                }
                ttsRequest.set(item.ttsConfig ?: contract.ttsConfig ?: TtsConfig.DEFAULT_EN)
                val audio = dataService.getTtsAudio(
                        speechProvider,
                        ttsRequest,
                        clientRequirements.tts != BotClientRequirements.TtsType.RequiredLinks,
                        clientRequirements.tts == BotClientRequirements.TtsType.RequiredStreaming
                )
                when (clientRequirements.tts) {
                    BotClientRequirements.TtsType.RequiredLinks ->
                        item.audio = "/file/${audio.fileId}" // caller must know port URL therefore URI is enough

                    BotClientRequirements.TtsType.RequiredStreaming ->
                        sendBinaryData(audio.speak().data!!)
                }
            }
        }
        sendEvent(BotEvent(BotEvent.Type.Message, message))
    }

}