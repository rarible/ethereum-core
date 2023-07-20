package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.cache.CacheableMonoEthereum
import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import scalether.core.MonoEthereum

@Configuration
@ImportAutoConfiguration(EthereumTransportConfiguration::class)
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnClass(MonoEthereum::class, WebClientTransport::class)
class EthereumAutoConfiguration(
    private val ethereumProperties: EthereumProperties,
) {

    @Bean
    @ConditionalOnMissingBean(MonoEthereum::class)
    @ConditionalOnBean(EthereumTransport::class)
    fun ethereum(ethereumTransport: EthereumTransport) = with(ethereumProperties) {
        if (cache.enabled) {
            CacheableMonoEthereum(
                transport = ethereumTransport.httpTransport,
                expireAfter = cache.expireAfter,
                cacheMaxSize = cache.maxSize,
            )
        } else MonoEthereum(ethereumTransport.httpTransport)
    }
}
