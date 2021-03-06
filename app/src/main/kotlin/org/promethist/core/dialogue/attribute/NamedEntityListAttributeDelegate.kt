package org.promethist.core.dialogue.attribute

import org.promethist.core.dialogue.AbstractDialogue
import org.promethist.core.type.NamedEntity
import org.promethist.core.type.StringMutableList
import kotlin.reflect.KProperty

class NamedEntityListAttributeDelegate<E: NamedEntity>(
        val entities: Collection<E>,
        scope: ContextualAttributeDelegate.Scope,
        namespace: (() -> String)
) {
    private val attributeDelegate = ContextualAttributeDelegate(scope, StringMutableList::class, namespace) { StringMutableList() }

    operator fun getValue(thisRef: AbstractDialogue, property: KProperty<*>): MutableList<E> {
        val names = attributeDelegate.getValue(thisRef, property)
        return object : ArrayList<E>(names.mapNotNull { name -> entities.find { it.name == name } }) {
            override fun add(entity: E): Boolean {
                names.add(entity.name)
                attributeDelegate.setValue(thisRef, property, names)
                return super.add(entity)
            }

            override fun addAll(entities: Collection<E>): Boolean {
                entities.forEach { names.add(it.name) }
                attributeDelegate.setValue(thisRef, property, names)
                return super.addAll(entities)
            }

            override fun remove(entity: E): Boolean {
                names.remove(entity.name)
                attributeDelegate.setValue(thisRef, property, names)
                return super.remove(entity)
            }

            override fun clear() {
                names.clear()
                attributeDelegate.setValue(thisRef, property, names)
                super.clear()
            }
        }
    }
}