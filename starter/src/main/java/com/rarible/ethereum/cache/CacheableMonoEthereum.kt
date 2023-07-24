package com.rarible.ethereum.cache

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Word
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.response.Block
import scalether.domain.response.Transaction
import java.time.Duration

class CacheableMonoEthereum(
    transport: MonoRpcTransport,
    expireAfter: Duration,
    cacheMaxSize: Long,
) : MonoEthereum(transport) {

    private val blockByHashCache: AsyncLoadingCache<Word, Block<Transaction>> = Caffeine.newBuilder()
        .expireAfterWrite(expireAfter)
        .maximumSize(cacheMaxSize)
        .buildAsync { key, _ -> super.ethGetFullBlockByHash(key).toFuture() }

    override fun ethGetFullBlockByHash(hash: Word): Mono<Block<Transaction>> {
        return Mono.fromFuture(blockByHashCache.get(hash))
    }
}
