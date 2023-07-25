package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.MonoEthereum
import scalether.core.PubSubTransport
import scalether.transport.WebSocketPubSubTransport

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnEthereumNodesProperty
@ConditionalOnClass(MonoEthereum::class, WebSocketPubSubTransport::class, WebClientTransport::class)
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
