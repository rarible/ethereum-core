package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
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
    var node: AtomicReference<EthereumTransport> = AtomicReference(aliveNode())

    fun rediscover() {
        val newNode = aliveNode()
        node.set(newNode)
    }

    private fun aliveNode(): EthereumTransport {
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
                    super.get(url, manifest).retryWhen(retry)

                override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> =
                    super.send(request, manifest).retryWhen(retry)
            }
        }

    private fun nodeAvailable(rpcUrl: String, rpcTransport: WebClientTransport): Boolean =
        try {
            MonoEthereum(rpcTransport).netListening().block() as Boolean
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
        val delegate = ethereumTransportProvider.node.get().websocketTransport
        return delegate.subscribe(name, param, manifest)
            .doOnError {
                logger.error("PubSub transport encountered an error", it)
                ethereumTransportProvider.rediscover()
            }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FailoverPubSubTransport::class.java)
    }
}

class FailoverRpcTransport(private val ethereumTransportProvider: EthereumTransportProvider) : MonoRpcTransport {
    override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> {
        val rpcTransport = ethereumTransportProvider.node.get().rpcTransport
        return rpcTransport.send(request, manifest)
            .doOnError {
                logger.error("Rpc transport encountered an error", it)
                ethereumTransportProvider.rediscover()
            }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EthereumTransportProvider::class.java)
    }
}
