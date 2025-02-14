package com.rarible.ethereum.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.rarible.ethereum.client.mapper.registerWordDeserializer
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
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

    private fun shouldRetry(ex: Throwable): Boolean {
        return !is4xxError(ex) && (ex is WebClientException || ex is IOException || ex is ChannelException)
    }

    private fun is4xxError(ex: Throwable) = ex is WebClientResponseException && ex.statusCode.is4xxClientError

    protected fun httpTransport(
        httpUrl: String,
        headers: Map<String, String>? = null,
        requestTimeoutMs: Int,
        readWriteTimeoutMs: Int,
        maxFrameSize: Int,
        retryMaxAttempts: Long,
        retryBackoffDelay: Long,
        allowTransactionsWithoutHash: Boolean
    ): WebClientTransport {
        val retry = Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffDelay)).filter { shouldRetry(it) }
        val mapper = MonoEthereum.mapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        if (allowTransactionsWithoutHash) {
            mapper.registerWordDeserializer()
        }
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
