package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.EventData
import io.daonomic.rpc.domain.Word
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import scalether.domain.Address
import scalether.domain.response.Log

interface LogEventDescriptor<T : EventData> {
    val collection: String
    val topic: Word

    fun convert(log: Log, timestamp: Long): Publisher<T>
    fun getAddresses(): Mono<Collection<Address>>
}