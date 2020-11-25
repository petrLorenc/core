package com.promethist.common

import com.promethist.util.LoggerDelegate

object ServiceUrlResolver {

    val logger by LoggerDelegate()

    val servicePorts = mapOf(
            "bot" to 3000,
            "core" to 8080,
            "admin" to 8089,
            "illusionist" to 8090,
            "cassandra" to 8093,
            "cassandra-training" to 8094,
            "illusionist-training" to 8095,
            "duckling" to 8096
    )

    enum class RunMode { local, docker, dist, detect }
    enum class Protocol { http, ws }

    fun getEndpointUrl(serviceName: String, runMode: RunMode = RunMode.detect, protocol: Protocol = Protocol.http, namespace: String? = AppConfig.instance["namespace"], domain: String = "promethist.com"): String {
        return AppConfig.instance.getOrNull("$serviceName.url")?.let { url ->
            url.replaceFirst("http", protocol.name)
        } ?: when (runMode) {
            RunMode.local -> "${protocol}://localhost:${servicePorts[serviceName] ?: error("Port for service $serviceName not defined.")}"
            RunMode.docker -> "${protocol}://${serviceName}:8080"
            RunMode.dist -> "${protocol}s://" + serviceName + (if (namespace != "default") ".$namespace" else "") + "." + domain
            RunMode.detect -> getEndpointUrl(serviceName,
                    RunMode.valueOf(System.getenv("RUN_MODE") ?: AppConfig.instance.get("runmode", RunMode.dist.name)),
                    protocol, namespace, domain)
        }
    }
}