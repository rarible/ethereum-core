package com.rarible.ethereum.client.trace

import com.fasterxml.jackson.databind.JsonNode
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import java.time.Duration

class CacheableTransactionTraceClient(
    private val ethereum: MonoEthereum,
    private val cacheEnabled: Boolean,
    cacheSize: Long,
    cacheTtl: Duration
) {

    private val cache: AsyncLoadingCache<Request, Response<JsonNode>> = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterWrite(cacheTtl)
        .buildAsync { key, _ ->
            execute(key).toFuture()
        }

    suspend fun getTrace(request: Request): Response<JsonNode> {
        if (!cacheEnabled) {
            return execute(request).awaitSingle()
        }
        return cache.get(request).await()
    }

    private fun execute(request: Request): Mono<Response<JsonNode>> {
        return ethereum.executeRaw(request)
    }
}
