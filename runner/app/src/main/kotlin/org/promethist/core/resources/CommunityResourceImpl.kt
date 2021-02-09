package org.promethist.core.resources

import com.mongodb.client.MongoDatabase
import org.litote.kmongo.*
import org.promethist.common.security.Authorized
import org.promethist.core.model.Community
import javax.inject.Inject
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/communities")
@Produces(MediaType.APPLICATION_JSON)
@Authorized
class CommunityResourceImpl : CommunityResource {

    @Inject
    lateinit var database: MongoDatabase

    private val communities by lazy { database.getCollection<Community>() }
    override fun getCommunities(): List<Community> {
        return communities.find().toMutableList()
    }

    override fun getCommunitiesInSpace(spaceId: String): List<Community> {
        return communities.find(or(Community::space_id eq spaceId, Community::organization_id eq spaceId)).toMutableList()
    }

    override fun get(communityName: String, spaceId: String): Community? {
        return communities.find(Community::name eq communityName, or(Community::space_id eq spaceId, Community::organization_id eq spaceId)).singleOrNull()
    }

    override fun create(community: Community) {
        communities.insertOne(community)
    }

    override fun update(community: Community) {
        communities.updateOneById(community._id, community, upsert())
    }
}