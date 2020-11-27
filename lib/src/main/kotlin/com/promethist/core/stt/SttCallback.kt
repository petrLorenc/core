package com.promethist.core.stt

import com.promethist.core.Input

interface SttCallback {

    fun onResponse(input: Input, isFinal: Boolean)

    fun onOpen()

    fun onError(e: Throwable)

    fun onEndOfUtterance()
}