package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.LogEvent
import io.daonomic.rpc.domain.Word
import reactor.core.publisher.Mono

interface OnLogEventListener {

    val topics: List<Word>

    fun onLogEvent(logEvent: LogEvent): Mono<Void>

    fun onRevertedLogEvent(logEvent: LogEvent): Mono<Void>
}
