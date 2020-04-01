package com.promethist.core.model

import com.promethist.core.Response
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import com.promethist.core.model.Message as CoreMessage
import java.util.*

typealias SessionAttributes = MutableMap<String, Any>

data class Session(
        val _id: Id<Session> = newId(),
        val datetime: Date = Date(),
        val sessionId: String,
        var user: User,
        var application: Application,
        val turns: MutableList<Turn> = mutableListOf(),
        val messages: MutableList<Message> = mutableListOf(),
        val metrics: MutableList<Metric> = mutableListOf(Metric("session", "Count", 1)),
        val attributes: SessionAttributes = mutableMapOf()
) {
    data class Metric(val namespace: String, val name: String, var value: Long = 0) {
        fun increment() = value++
    }

    data class Message(
            val datetime: Date?,
            val sender: String?,
            val recipient: String?,
            val items: MutableList<Response.Item>
    ) {
        constructor(message: CoreMessage) : this(message.datetime, message.sender, message.recipient, message.items)
    }

    fun addMessage(message: Message) = messages.add(message)
    fun addMessage(message: CoreMessage) = messages.add(Message(message))
}