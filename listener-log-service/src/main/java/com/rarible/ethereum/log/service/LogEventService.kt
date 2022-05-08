package com.rarible.ethereum.log.service

import com.rarible.ethereum.listener.log.domain.LogEvent
import io.daonomic.rpc.domain.Word
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import reactor.core.publisher.Mono

class LogEventService(
    val map: Map<Word, String>,
    private val mongo: ReactiveMongoOperations
) {
    fun save(event: LogEvent): Mono<LogEvent> =
        mongo.save(event.withDbUpdated(), map.getValue(event.topic))
}