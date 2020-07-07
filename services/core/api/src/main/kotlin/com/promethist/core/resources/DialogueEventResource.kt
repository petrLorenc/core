package com.promethist.core.resources

import com.promethist.core.model.DialogueEvent
import com.promethist.core.model.Session
import io.swagger.annotations.Api
import org.litote.kmongo.Id
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Api(tags = ["Dialogue Event"])
@Path("/dialogueEvent")
@Produces(MediaType.APPLICATION_JSON)
interface DialogueEventResource {
    @GET
    fun getDialogueEvents():List<DialogueEvent>

    fun get(eventId: Id<DialogueEvent>): DialogueEvent?
    fun create(dialogueEvent: DialogueEvent)
}