package com.rarible.ethereum.listener.log.block

import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.time.delay
import reactor.core.publisher.Flux
import scalether.core.MonoEthereum
import scalether.domain.response.Block
import java.time.Duration

@ExperimentalCoroutinesApi
class NewBlockPoller(
    private val ethereum: MonoEthereum,
    private val pollingDelay: Duration
) : NewBlockSubscriber {

    override fun newHeads(): Flux<Block<Word>> = channelFlow<Block<Word>> {
        while (isClosedForSend.not()) {
            val headBlockNumber = ethereum.ethBlockNumber().awaitFirst()
            val head = ethereum.ethGetBlockByNumber(headBlockNumber).awaitFirstOrNull()
            if (head != null) send(head)
            delay(pollingDelay)
        }
    }.asFlux()
}
