package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.client.EthereumNode
import com.rarible.ethereum.client.EthereumTransportProvider
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
                maxBlockDelay = ethereumProperties.maxBlockDelay,
                allowTransactionsWithoutHash = ethereumProperties.allowTransactionsWithoutHash,
                mediaType = ethereumProperties.mediaType,
            )
        } else {
            LegacyEthereumTransportProvider(
                node = EthereumNode(
                    httpUrl = ethereumProperties.httpUrl!!,
                ),
                requestTimeoutMs = ethereumProperties.requestTimeoutMs,
                readWriteTimeoutMs = ethereumProperties.readWriteTimeoutMs,
                maxFrameSize = ethereumProperties.maxFrameSize,
                retryMaxAttempts = ethereumProperties.retryMaxAttempts,
                retryBackoffDelay = ethereumProperties.retryBackoffDelay,
                allowTransactionsWithoutHash = ethereumProperties.allowTransactionsWithoutHash,
                mediaType = ethereumProperties.mediaType,
            )
        }

    @Bean
    fun reconciliationEthereumTransportProvider(
        ethereumTransportProvider: EthereumTransportProvider
    ): EthereumTransportProvider {
        return if (ethereumProperties.reconciliationNodes.isEmpty()) {
            ethereumTransportProvider
        } else {
            HaEthereumTransportProvider(
                localNodes = ethereumProperties.reconciliationNodes,
                externalNodes = ethereumProperties.externalNodes,
                requestTimeoutMs = ethereumProperties.requestTimeoutMs,
                readWriteTimeoutMs = ethereumProperties.readWriteTimeoutMs,
                maxFrameSize = ethereumProperties.maxFrameSize,
                retryMaxAttempts = ethereumProperties.retryMaxAttempts,
                retryBackoffDelay = ethereumProperties.retryBackoffDelay,
                monitoringThreadInterval = ethereumProperties.monitoringThreadInterval,
                maxBlockDelay = ethereumProperties.maxBlockDelay,
                allowTransactionsWithoutHash = ethereumProperties.allowTransactionsWithoutHash,
                mediaType = ethereumProperties.mediaType,
            )
        }
    }

    @Bean
    fun mainEthereumRpc(ethereumTransportProvider: EthereumTransportProvider): MonoRpcTransport =
        ethereumRpc(ethereumTransportProvider)

    @Bean
    fun reconciliationEthereumRpc(reconciliationEthereumTransportProvider: EthereumTransportProvider): MonoRpcTransport =
        ethereumRpc(reconciliationEthereumTransportProvider)

    private fun ethereumRpc(ethereumTransportProvider: EthereumTransportProvider): MonoRpcTransport {
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
}
