package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.MonoEthereum

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnProperty(prefix = RARIBLE_ETHEREUM, name = ["httpUrl"], matchIfMissing = false)
@ConditionalOnClass(MonoEthereum::class, WebClientTransport::class)
class EthereumAutoConfiguration(
    private val ethereumProperties: EthereumProperties
) {

    @Bean
    @ConditionalOnMissingBean(MonoEthereum::class)
    fun ethereum() = with (ethereumProperties) {
        MonoEthereum(object : WebClientTransport(httpUrl, MonoEthereum.mapper(), requestTimeoutMs, readWriteTimeoutMs) {
            override fun maxInMemorySize(): Int = maxFrameSize
        })
    }
}