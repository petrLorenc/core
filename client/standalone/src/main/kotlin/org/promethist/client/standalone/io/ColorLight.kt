package org.promethist.client.standalone.io

import java.awt.Color

interface ColorLight: Light {

    fun set(color: Color)
}