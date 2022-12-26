package com.rarible.ethereum.listener.log.block

import io.daonomic.rpc.domain.Word
import reactor.core.publisher.Flux
import scalether.domain.response.Block

interface NewBlockSubscriber {
    fun newHeads(): Flux<Block<Word>>
}