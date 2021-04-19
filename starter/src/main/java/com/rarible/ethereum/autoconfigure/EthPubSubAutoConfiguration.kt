package com.rarible.ethereum.autoconfigure

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.EthPubSub
import scalether.transport.WebSocketPubSubTransport

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnProperty(prefix = RARIBLE_ETHEREUM, name = ["websocketUrl"], matchIfMissing = false)
@ConditionalOnClass(WebSocketPubSubTransport::class, EthPubSub::class)
class EthPubSubAutoConfiguration(
    private val ethereumProperties: EthereumProperties
) {

    @Bean
    @ConditionalOnMissingBean(EthPubSub::class)
    fun ethPubSub() = with (ethereumProperties) {
        EthPubSub(WebSocketPubSubTransport(websocketUrl, maxFrameSize))
    }
}