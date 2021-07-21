package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.MonoEthereum
import scalether.core.MonoParity

@Configuration
@EnableConfigurationProperties(EthereumParityProperties::class)
@ConditionalOnProperty(prefix = RARIBLE_ETHEREUM_PARITY, name = ["httpUrl"], matchIfMissing = false)
@ConditionalOnClass(MonoParity::class, WebClientTransport::class)
class EthereumMonoParityAutoConfiguration(
    private val ethereumParityProperties: EthereumParityProperties
) {
    @Bean
    @ConditionalOnMissingBean(MonoParity::class)
    fun monoParity() = with (ethereumParityProperties) {
        MonoParity(object : WebClientTransport(httpUrl, MonoEthereum.mapper(), requestTimeoutMs, readWriteTimeoutMs) {
            override fun maxInMemorySize(): Int = maxFrameSize
        })
    }
}
