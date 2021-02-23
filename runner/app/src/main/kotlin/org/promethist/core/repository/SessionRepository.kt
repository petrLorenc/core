package org.promethist.core.repository

import org.litote.kmongo.Id
import org.promethist.common.query.Query
import org.promethist.common.repository.EntityRepository
import org.promethist.core.model.Session
import org.promethist.core.model.User

interface SessionRepository : EntityRepository<Session> {
    fun findBy(userId: Id<User>): List<Session>
    fun findBy(sessionId: String): Session?
}