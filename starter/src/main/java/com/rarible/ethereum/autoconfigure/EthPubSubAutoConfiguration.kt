package com.rarible.ethereum.autoconfigure

import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.EthPubSub

@Configuration
@ImportAutoConfiguration(EthereumTransportConfiguration::class)
@ConditionalOnClass(EthPubSub::class)
class EthPubSubAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EthPubSub::class)
    @ConditionalOnBean(EthereumTransport::class)
    fun ethPubSub(ethereumTransport: EthereumTransport) = EthPubSub(ethereumTransport.websocketTransport)
}