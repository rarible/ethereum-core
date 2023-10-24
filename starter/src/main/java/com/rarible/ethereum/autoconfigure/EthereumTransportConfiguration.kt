package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.client.EthereumNode
import com.rarible.ethereum.client.EthereumTransportProvider
import com.rarible.ethereum.client.FailoverPubSubTransport
import com.rarible.ethereum.client.FailoverRpcTransport
import com.rarible.ethereum.client.HaEthereumTransportProvider
import com.rarible.ethereum.client.LegacyEthereumTransportProvider
import com.rarible.ethereum.client.failover.CompositeFailoverPredicate
import com.rarible.ethereum.client.failover.NoopFailoverPredicate
import com.rarible.ethereum.client.failover.SimplePredicate
import io.daonomic.rpc.MonoRpcTransport
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.PubSubTransport

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnEthereumNodesProperty
class EthereumTransportConfiguration(
    private val ethereumProperties: EthereumProperties,
) {
    @Bean
    fun ethereumTransportProvider(): EthereumTransportProvider =
        if (ethereumProperties.nodes.isNotEmpty()) {
            HaEthereumTransportProvider(
                localNodes = ethereumProperties.nodes,
                externalNodes = ethereumProperties.externalNodes,
                requestTimeoutMs = ethereumProperties.requestTimeoutMs,
                readWriteTimeoutMs = ethereumProperties.readWriteTimeoutMs,
                maxFrameSize = ethereumProperties.maxFrameSize,
                retryMaxAttempts = ethereumProperties.retryMaxAttempts,
                retryBackoffDelay = ethereumProperties.retryBackoffDelay,
                monitoringThreadInterval = ethereumProperties.monitoringThreadInterval,
                maxBlockDelay = ethereumProperties.maxBlockDelay
            )
        } else {
            LegacyEthereumTransportProvider(
                node = EthereumNode(
                    httpUrl = ethereumProperties.httpUrl!!,
                    websocketUrl = ethereumProperties.websocketUrl!!,
                ),
                requestTimeoutMs = ethereumProperties.requestTimeoutMs,
                readWriteTimeoutMs = ethereumProperties.readWriteTimeoutMs,
                maxFrameSize = ethereumProperties.maxFrameSize,
                retryMaxAttempts = ethereumProperties.retryMaxAttempts,
                retryBackoffDelay = ethereumProperties.retryBackoffDelay,
            )
        }

    @Bean
    fun ethereumRpc(ethereumTransportProvider: EthereumTransportProvider): MonoRpcTransport {
        val predicate = if (ethereumProperties.failoverEnabled) {
            CompositeFailoverPredicate(
                failoverPredicates = listOf(
                    SimplePredicate(code = -32000, errorMessagePrefix = "required historical state unavailable"),
                    SimplePredicate(code = -32601, errorMessagePrefix = "the method"),
                )
            )
        } else {
            NoopFailoverPredicate()
        }
        return FailoverRpcTransport(
            ethereumTransportProvider = ethereumTransportProvider,
            failoverPredicate = predicate
        )
    }

    @Bean
    fun ethereumPubSub(ethereumTransportProvider: EthereumTransportProvider): PubSubTransport =
        FailoverPubSubTransport(ethereumTransportProvider)
}
