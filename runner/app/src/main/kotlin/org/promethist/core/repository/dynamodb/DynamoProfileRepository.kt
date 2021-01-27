@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package org.promethist.core.repository.dynamodb

import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.KeyAttribute
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.utils.NameMap
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import org.litote.kmongo.*
import org.promethist.common.ObjectUtil
import org.promethist.common.query.DynamoDbFiltersFactory
import org.promethist.common.query.Query
import org.promethist.core.model.Profile
import org.promethist.core.model.Space
import org.promethist.core.model.User
import org.promethist.core.repository.ProfileRepository
import kotlin.collections.toList

class DynamoProfileRepository : DynamoAbstractEntityRepository<Profile>(), ProfileRepository {

    private val profilesTable by lazy { database.getTable(tableName("profile")) }

    override fun findBy(userId: Id<User>, spaceId: Id<Space>): Profile? {
        val spec = ScanSpec()
            .withFilterExpression("#userid = :value and #spaceid = :value2")
            .withNameMap(NameMap()
                .with("#userid", "user_id")
                .with("#spaceid", "space_id"))
            .withValueMap(ValueMap()
                .withString(":value", userId.toString())
                .withString(":value2", spaceId.toString())
            )
        return profilesTable.scan(spec).map { item -> ObjectUtil.defaultMapper.readValue(item.toJSON(), Profile::class.java) }.singleOrNull()
    }

    override fun get(id: Id<Profile>): Profile? {
        return profilesTable.getItem(KeyAttribute("_id", id.toString()))?.let {
            ObjectUtil.defaultMapper.readValue(it.toJSON(), Profile::class.java)
        }
    }

    override fun find(query: Query): List<Profile> {
        val spec = ScanSpec()
        val (filterExpression, nameMap, valueMap) = DynamoDbFiltersFactory.createFilters(query)

        spec.withFilterExpression(filterExpression.joinToString(separator = " and "))
        spec.withNameMap(nameMap)
        spec.withValueMap(valueMap)
        spec.withMaxResultSize(query.limit)
        return profilesTable.scan(spec).map { item -> ObjectUtil.defaultMapper.readValue(item.toJSON(), Profile::class.java) }
    }


    override fun getAll(): List<Profile> = profilesTable.scan().toList().map { item -> ObjectUtil.defaultMapper.readValue(item.toJSON(), Profile::class.java) }

    override fun create(profile: Profile): Profile {
        profilesTable.putItem(Item.fromJSON(ObjectUtil.defaultMapper.writeValueAsString(profile)))
        return profile
    }

    override fun update(entity: Profile, upsert: Boolean): Profile {
        return create(entity)
    }

}