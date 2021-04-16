package com.rarible.ethereum.listener.log.mock

import com.rarible.contracts.test.erc20.TransferEvent
import com.rarible.ethereum.listener.log.LogEventDescriptor
import com.rarible.rpc.domain.Word
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import scalether.domain.Address
import scalether.domain.response.Log

@Component
class TransferEventDescriptor : LogEventDescriptor<Transfer> {

    override val collection: String = "transfer"
    override val topic: Word = TransferEvent.id()

    override fun convert(log: Log, timestamp: Long): Mono<Transfer> {
        val scalether = TransferEvent.apply(log)
        return Mono.just(Transfer(
            from = scalether.from(),
            to = scalether.to(),
            value = scalether.value()
        ))
    }

    override fun getAddresses(): Mono<Collection<Address>> {
        return Mono.just(emptyList())
    }
}