package com.rarible.ethereum.client

import com.fasterxml.jackson.annotation.JsonInclude
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.collection.immutable.Map
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import java.io.IOException
import java.time.Duration

abstract class EthereumTransportProvider {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    abstract fun rpcError()
    abstract suspend fun getRpcTransport(): WebClientTransport
    abstract suspend fun getFailoverRpcTransport(): WebClientTransport?

    protected fun httpTransport(
        httpUrl: String,
        headers: Map<String, String>? = null,
        requestTimeoutMs: Int,
        readWriteTimeoutMs: Int,
        maxFrameSize: Int,
        retryMaxAttempts: Long,
        retryBackoffDelay: Long,
    ): WebClientTransport {
        val retry = Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffDelay))
            .filter { it is WebClientException || it is IOException || it is ChannelException }
        val mapper = MonoEthereum.mapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        return object : WebClientTransport(
            httpUrl,
            mapper,
            requestTimeoutMs,
            readWriteTimeoutMs
        ) {
            override fun headers() = headers ?: super.headers()
            override fun maxInMemorySize(): Int = maxFrameSize
            override fun <T : Any?> get(url: String?, manifest: Manifest<T>?): Mono<T> =
                super.get(url, manifest)
                    .logOnErrorAndRetry()

            override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> =
                super.send(request, manifest)
                    .logOnErrorAndRetry(request)

            private fun <T : Any?> Mono<T>.logOnErrorAndRetry(request: Request? = null): Mono<T> = doOnError {
                val body = mapper.writeValueAsString(request)
                logger.warn("Failed request: $body. ${it.message}", it)
            }.retryWhen(retry)
        }
    }
}
