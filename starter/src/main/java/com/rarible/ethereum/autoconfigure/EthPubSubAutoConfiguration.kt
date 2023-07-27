package com.rarible.ethereum.autoconfigure

import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.EthPubSub
import scalether.core.PubSubTransport

@Configuration
@ImportAutoConfiguration(EthereumTransportConfiguration::class)
class EthPubSubAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EthPubSub::class)
    @ConditionalOnBean(PubSubTransport::class)
    fun ethPubSub(webSocketTransport: PubSubTransport) = EthPubSub(webSocketTransport)
}
