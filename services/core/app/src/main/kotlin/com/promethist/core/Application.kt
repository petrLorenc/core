package com.promethist.core

import com.mongodb.ConnectionString
import com.mongodb.client.MongoDatabase
import com.promethist.common.*
import com.promethist.common.query.Query
import com.promethist.common.query.QueryInjectionResolver
import com.promethist.common.query.QueryParams
import com.promethist.common.query.QueryValueFactory
import com.promethist.core.context.ContextFactory
import com.promethist.core.context.ContextPersister
import com.promethist.core.components.*
import com.promethist.core.profile.MongoProfileRepository
import com.promethist.core.profile.ProfileRepository
import com.promethist.core.resources.*
import com.promethist.core.runtime.DialogueManager
import com.promethist.core.runtime.FileResourceLoader
import org.glassfish.hk2.api.InjectionResolver
import org.glassfish.hk2.api.PerLookup
import org.glassfish.hk2.api.TypeLiteral
import org.litote.kmongo.KMongo
import javax.inject.Singleton

class Application : JerseyApplication() {

    init {
        register(object : ResourceBinder() {
            override fun configure() {

                val runMode = ServiceUrlResolver.RunMode.valueOf(AppConfig.instance.get("runmode", "dist"))
                AppConfig.instance.let {
                    println("Promethist ${it["git.ref"]} core starting (namespace = ${it["namespace"]}, runMode = $runMode)")
                }

                // filestore
                val filestore = RestClient.instance(FileResource::class.java, ServiceUrlResolver.getEndpointUrl("filestore"))
                bind(filestore).to(FileResource::class.java)

                // NLP pipeline
                bindTo(Pipeline::class.java)
                bindTo(ContextFactory::class.java)
                bindTo(ContextPersister::class.java)

                // NLP components - order is important
                // tokenizer
                bind(InternalTokenizer()).to(Component::class.java).named("tokenizer")

                // IR component
                val illusionist = Illusionist()
                illusionist.webTarget = RestClient.webTarget(ServiceUrlResolver.getEndpointUrl("illusionist"))
                        .path("/query")
                        .queryParam("key", AppConfig.instance["illusionist.apiKey"])
                bind(illusionist).to(Component::class.java).named("illusionist")

                // NER component
                val cassandra = Cassandra()
                cassandra.webTarget = RestClient.webTarget(ServiceUrlResolver.getEndpointUrl("cassandra"))
                        .path("/query")
                        .queryParam("input_tokenized", true)
                        .queryParam("output_tokenized", true)
                bind(cassandra).to(Component::class.java).named("cassandra")

                // DM component
                val dm = DialogueManager(FileResourceLoader(filestore, "dialogue"))
                bind(dm).to(Component::class.java).named("dm")

                bind(MongoProfileRepository::class.java).to(ProfileRepository::class.java)
                bindTo(SessionResource::class.java, SessionResourceImpl::class.java)
                bindTo(MongoDatabase::class.java,
                        KMongo.createClient(ConnectionString(AppConfig.instance["database.url"]))
                                .getDatabase(AppConfig.instance["database.name"]))

                // dialogue manager helena (support of running V1 dialogue models)
                bindTo(BotService::class.java, ServiceUrlResolver.getEndpointUrl("helena") + "/dm")

                bindTo(CoreResourceImpl::class.java)

                // admin
                bindTo(ContentDistributionResource::class.java, ServiceUrlResolver.getEndpointUrl("admin"))

                bindFactory(QueryValueFactory::class.java).to(Query::class.java).`in`(PerLookup::class.java)

                bind(QueryInjectionResolver::class.java)
                        .to(object: TypeLiteral<InjectionResolver<QueryParams>>() {})
                        .`in`(Singleton::class.java)

            }
        })
    }
}