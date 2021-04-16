package com.rarible.ethereum.block

import com.rarible.rpc.domain.Bytes
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface BlockState<B> {
    fun getLastKnownBlock(): Mono<Long>
    fun getBlockHash(number: Long): Mono<Bytes>
    fun saveKnownBlock(block: B): Mono<Void>
}

interface Blockchain<B> {
    fun getLastKnownBlock(): Mono<Long>
    fun getBlock(hash: Bytes): Mono<B>
    fun getBlock(number: Long): Mono<B>
    fun listenNewBlocks(): Flux<B>
}
