package com.rarible.ethereum.client

import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import scalether.transport.WebSocketPubSubTransport
import java.io.IOException
import java.time.Duration

abstract class EthereumTransportProvider {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    abstract fun websocketDisconnected()
    abstract fun rpcError()
    abstract fun registerWebsocketSubscription(reconnect: () -> Unit)
    abstract fun unregisterWebsocketSubscription(reconnect: () -> Unit)
    abstract suspend fun getWebsocketTransport(): WebSocketPubSubTransport
    abstract suspend fun getRpcTransport(): WebClientTransport
    abstract suspend fun getFailoverRpcTransport(): WebClientTransport?

    protected fun httpTransport(
        httpUrl: String,
        requestTimeoutMs: Int,
        readWriteTimeoutMs: Int,
        maxFrameSize: Int,
        retryMaxAttempts: Long,
        retryBackoffDelay: Long,
    ): WebClientTransport {
        val retry = Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffDelay))
            .filter { it is WebClientException || it is IOException || it is ChannelException }
        return object : WebClientTransport(
            httpUrl,
            MonoEthereum.mapper(),
            requestTimeoutMs,
            readWriteTimeoutMs
        ) {
            override fun maxInMemorySize(): Int = maxFrameSize
            override fun <T : Any?> get(url: String?, manifest: Manifest<T>?): Mono<T> =
                super.get(url, manifest)
                    .doOnError {
                        logger.warn(it.message, it)
                    }
                    .retryWhen(retry)

            override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> =
                super.send(request, manifest)
                    .doOnError {
                        logger.warn(it.message, it)
                    }
                    .retryWhen(retry)
        }
    }
}
