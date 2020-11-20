package com.promethist.core.runtime

import com.promethist.core.model.Community
import com.promethist.core.resources.CommunityResource

class SimpleCommunityResource : CommunityResource {

    private val communities = mutableMapOf<String, Community>()
    override fun getCommunities(): List<Community> {
        return communities.values.toMutableList()
    }

    override fun getCommunitiesInOrganization(organizationId: String): List<Community> {
        return communities.values.filter { it.organization_id == organizationId }
    }

    override fun get(communityName: String, organizationId: String): Community? = communities[communityName]

    override fun create(community: Community) {
        communities[community.name] = community
    }

    override fun update(community: Community) {
        communities[community.name] = community
    }
}