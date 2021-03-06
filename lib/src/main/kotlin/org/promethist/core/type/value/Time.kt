package org.promethist.core.type.value

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.promethist.common.ObjectUtil
import org.promethist.core.type.DateTime

@JsonDeserialize(using = Time.Deserializer::class)
open class Time: Value() {

    class Deserializer : JsonDeserializer<Time?>() {
        override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext?): Time {
            val value: JsonNode = ObjectUtil.defaultMapper.readTree(jsonParser)
            return if (value.has("type") && value["type"].asText() == "interval") {
                val from = if (value.has("from")) ObjectUtil.defaultMapper.treeToValue(value["from"], GrainedTime::class.java) else null
                val to = if (value.has("to")) ObjectUtil.defaultMapper.treeToValue(value["to"], GrainedTime::class.java) else null
                Interval(from, to)
            } else {
                GrainedTime(DateTime.parse(value["value"].asText()), value["grain"].asText())
            }
        }
    }
}