package org.promethist.core.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import org.promethist.common.ServiceUrlResolver
import org.promethist.core.type.MutablePropertyMap

open class Application(
        open var _id: Id<Application> = newId(),
        open var name: String,
        open var dialogue_id: Id<DialogueModel>? = null,
        open var public: Boolean = false,
        open var anonymousAccessAllowed: Boolean = false,
        open var icon: String? = null,
        open var properties: MutablePropertyMap = mutableMapOf()
) {
    @get:JsonIgnore
    val link get() = ServiceUrlResolver.getEndpointUrl("bot", domain = "promethist.ai") + "/" + _id
}