package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClientException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import scalether.transport.WebSocketPubSubTransport
import java.io.IOException
import java.time.Duration

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnEthereumNodesProperty
@ConditionalOnClass(MonoEthereum::class, WebSocketPubSubTransport::class, WebClientTransport::class)
class EthereumTransportConfiguration {
    @Bean
    fun ethereumTransport(ethereumProperties: EthereumProperties): EthereumTransport {
        for (node in ethereumProperties.nodes) {
            logger.info("Using new node definition")
            val httpTransport = transport(node.httpUrl, ethereumProperties)
            if (nodeAvailable(httpTransport)) {
                return EthereumTransport(
                    httpTransport,
                    WebSocketPubSubTransport(node.websocketUrl, ethereumProperties.maxFrameSize)
                )
            }
        }
        if (ethereumProperties.httpUrl != null && ethereumProperties.websocketUrl != null) {
            logger.info("Using legacy node definition")
            return EthereumTransport(
                transport(ethereumProperties.httpUrl, ethereumProperties),
                WebSocketPubSubTransport(ethereumProperties.websocketUrl, ethereumProperties.maxFrameSize)
            )
        }
        throw IllegalStateException("None of nodes ${ethereumProperties.nodes} are available")
    }

    private fun transport(httpUrl: String, ethereumProperties: EthereumProperties): WebClientTransport =
        with(ethereumProperties) {
            val retry = Retry
                .backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffDelay))
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

    private fun nodeAvailable(transport: WebClientTransport): Boolean =
        try {
            MonoEthereum(transport).netListening().block() as Boolean
        } catch (e: Exception) {
            false
        }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EthereumTransportConfiguration::class.java)
    }
}

data class EthereumTransport(
    val httpTransport: WebClientTransport,
    val websocketTransport: WebSocketPubSubTransport,
)
