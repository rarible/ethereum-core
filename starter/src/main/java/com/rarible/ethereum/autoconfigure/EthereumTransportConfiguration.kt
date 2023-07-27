package com.rarible.ethereum.autoconfigure

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
    fun ethereumTransportProvider() = EthereumTransportProvider(ethereumProperties)

    @Bean
    fun ethereumRpc(ethereumTransportProvider: EthereumTransportProvider): MonoRpcTransport =
        FailoverRpcTransport(ethereumTransportProvider)

    @Bean
    fun ethereumPubSub(ethereumTransportProvider: EthereumTransportProvider): PubSubTransport =
        FailoverPubSubTransport(ethereumTransportProvider)
}
