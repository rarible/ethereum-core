package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.Option
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import scalether.core.PubSubTransport
import scalether.transport.WebSocketPubSubTransport
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class EthereumTransportProvider(private val ethereumProperties: EthereumProperties) {
    private val mutex = Mutex()
    private val node: AtomicReference<EthereumTransport> = AtomicReference()

    /**
     * In case websocket is disconnected we rediscover new node
     */
    fun websocketDisconnected() {
        node.set(null)
    }

    private suspend fun getNode(): EthereumTransport {
        val cachedNode = node.get()
        if (cachedNode != null) {
            return cachedNode
        }
        return mutex.withLock {
            val cachedNode = node.get()
            if (cachedNode != null) {
                return cachedNode
            }
            val aliveNode = aliveNode()
            node.set(aliveNode)
            aliveNode
        }
    }

    suspend fun getWebsocketTransport(): WebSocketPubSubTransport = getNode().websocketTransport

    suspend fun getRpcTransport(): WebClientTransport = getNode().rpcTransport

    private suspend fun aliveNode(): EthereumTransport {
        for (node in ethereumProperties.nodes) {
            logger.info("Using new node definition httpUrl={}, websocketUrl={}", node.httpUrl, node.websocketUrl)
            val httpTransport = httpTransport(node.httpUrl, ethereumProperties)
            if (nodeAvailable(node.httpUrl, httpTransport)) {
                return EthereumTransport(
                    httpTransport,
                    WebSocketPubSubTransport(node.websocketUrl, ethereumProperties.maxFrameSize)
                )
            }
        }
        if (ethereumProperties.httpUrl != null && ethereumProperties.websocketUrl != null) {
            logger.info(
                "Using legacy node definition httpUrl={}, websocketUrl={}",
                ethereumProperties.httpUrl,
                ethereumProperties.websocketUrl
            )
            return EthereumTransport(
                httpTransport(ethereumProperties.httpUrl, ethereumProperties),
                WebSocketPubSubTransport(ethereumProperties.websocketUrl, ethereumProperties.maxFrameSize)
            )
        }
        throw IllegalStateException("None of nodes ${ethereumProperties.nodes} are available")
    }

    private fun httpTransport(httpUrl: String, ethereumProperties: EthereumProperties): WebClientTransport =
        with(ethereumProperties) {
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

    private suspend fun nodeAvailable(rpcUrl: String, rpcTransport: WebClientTransport): Boolean =
        try {
            MonoEthereum(rpcTransport).netListening().awaitSingle() as Boolean
        } catch (e: Exception) {
            logger.warn("Error while calling node {}. Trying next node...", rpcUrl, e)
            false
        }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EthereumTransportProvider::class.java)
    }
}

data class EthereumTransport(
    val rpcTransport: WebClientTransport,
    val websocketTransport: WebSocketPubSubTransport,
)

class FailoverPubSubTransport(private val ethereumTransportProvider: EthereumTransportProvider) : PubSubTransport {
    override fun <T : Any?> subscribe(name: String?, param: Option<Any>?, manifest: Manifest<T>?): Flux<T> {
        return mono { ethereumTransportProvider.getWebsocketTransport() }.flatMapMany { delegate ->
            delegate.subscribe(name, param, manifest)
                .doOnComplete {
                    logger.error("PubSub transport disconnected")
                    ethereumTransportProvider.websocketDisconnected()
                }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FailoverPubSubTransport::class.java)
    }
}

class FailoverRpcTransport(private val ethereumTransportProvider: EthereumTransportProvider) : MonoRpcTransport {
    override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> {
        return mono { ethereumTransportProvider.getRpcTransport() }.flatMap { rpcTransport ->
            rpcTransport.send(request, manifest)
                .doOnError {
                    logger.error("Rpc transport encountered an error", it)
                }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EthereumTransportProvider::class.java)
    }
}
