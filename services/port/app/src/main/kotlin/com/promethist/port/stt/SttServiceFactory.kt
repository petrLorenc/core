package com.promethist.port.stt

import com.promethist.core.Input
import com.promethist.core.model.Message
import java.io.File

object SttServiceFactory {

    fun create(provider: String, config: SttConfig, expectedPhrases: List<Message.ExpectedPhrase>, callback: SttCallback): SttService {
        when (provider) {
            "Google" -> return GoogleSttService(config, callback, expectedPhrases)
            else -> throw NotImplementedError()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val speech = File("local/speech.mp3").readBytes()
        val client =
            create("google", SttConfig("cs-CZ", 44100),
                listOf(Message.ExpectedPhrase("Ano",1F), Message.ExpectedPhrase("Ne", 1F)),
                object : SttCallback {
                    override fun onResponse(input: Input, final: Boolean) {
                        println("SST response - transcript: ${input.transcript.text}, confidence: ${input.transcript.confidence}, final: $final")
                    }

                    override fun onOpen() {
                        println("SST open")
                    }

                    override fun onError(e: Throwable) {
                        println("SST error")
                        e.printStackTrace()
                    }
                }).createStream().write(speech, 0, speech.size)

        Thread.sleep(10000)
    }

}