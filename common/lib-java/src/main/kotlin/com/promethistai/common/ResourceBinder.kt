package com.promethistai.common

import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.hk2.utilities.binding.BindingBuilder
import org.glassfish.jersey.client.proxy.WebResourceFactory
import org.glassfish.jersey.jackson.JacksonFeature
import javax.ws.rs.client.ClientBuilder

abstract class ResourceBinder : AbstractBinder() {

    fun <I>bindTo(iface: Class<I>, type: Class<out I>): BindingBuilder<out I> {
        return bind(type).to(iface)
    }

    fun <I>bindTo(iface: Class<I>, targetUrl: String): BindingBuilder<I> {
        return bindFactory(object : Factory<I> {

            override fun provide(): I {
                return RestClient.instance(iface, targetUrl)
            }

            override fun dispose(obj: I) {
            }
        }).to(iface)
    }

    fun <I,T>bindTo(iface: Class<I>, obj: T): BindingBuilder<T> {
        return bindFactory(object : Factory<T> {

            override fun provide(): T {
                return obj
            }

            override fun dispose(obj: T) {
            }
        }).to(iface)
    }
}