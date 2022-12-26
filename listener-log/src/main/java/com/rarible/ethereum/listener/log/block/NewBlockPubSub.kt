package com.rarible.ethereum.listener.log.block

import io.daonomic.rpc.domain.Word
import reactor.core.publisher.Flux
import scalether.core.EthPubSub
import scalether.domain.response.Block

class NewBlockPubSub(
    private val ethPubSub: EthPubSub
) : NewBlockSubscriber {
    override fun newHeads(): Flux<Block<Word>> = ethPubSub.newHeads()
}