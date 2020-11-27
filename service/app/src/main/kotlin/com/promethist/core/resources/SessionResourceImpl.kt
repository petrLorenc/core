package com.promethist.core.resources

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.promethist.common.query.MongoFiltersFactory
import com.promethist.common.query.Query
import com.promethist.core.model.Application
import com.promethist.core.model.DialogueModel
import com.promethist.core.model.Session
import com.promethist.core.model.User
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.id.ObjectIdGenerator
import org.litote.kmongo.id.WrappedObjectId
import javax.inject.Inject
import javax.ws.rs.Path

@Path("/sessions")
class SessionResourceImpl: SessionResource {

    @Inject
    lateinit var database: MongoDatabase

    @Inject
    lateinit var query: Query

    private val sessions by lazy { database.getCollection<Session>() }

    override fun getSessions(): List<Session> {
        val pipeline: MutableList<Bson> = mutableListOf()
        pipeline.apply {
            query.seek_id?.let { seekId ->
                val seekDate = sessions.findOneById(ObjectIdGenerator.create(seekId))!!.datetime
                add(match(or(
                        Session::datetime lt seekDate,
                        and(
                                Session::datetime eq seekDate,
                                Session::_id lt ObjectIdGenerator.create(seekId)
                        )
                )))
            }

            add(sort(descending(Session::datetime, Session::_id)))
            add(match(*MongoFiltersFactory.createFilters(Session::class, query, includeSeek = false).toTypedArray()))
            add(limit(query.limit))
        }

        return sessions.aggregate(pipeline).toMutableList()
    }

    override fun create(session: Session) {
        sessions.insertOne(session)
    }

    override fun update(session: Session) {
        sessions.updateOneById(session._id, session, upsert())
    }

    override fun get(sessionId: String): Session? {
        return sessions.find(Session::sessionId eq sessionId).singleOrNull()
    }

    override fun getForUser(userId: Id<User>): List<Session> {
        return sessions.find(Session::user / User::_id eq userId).toMutableList()
    }
}