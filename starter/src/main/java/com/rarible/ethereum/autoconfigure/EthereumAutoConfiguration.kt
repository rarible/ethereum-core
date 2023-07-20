package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.cache.CacheableMonoEthereum
import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.MonoEthereum

@Configuration
@ImportAutoConfiguration(EthereumTransportConfiguration::class)
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnClass(MonoEthereum::class, MonoRpcTransport::class)
class EthereumAutoConfiguration(
    private val ethereumProperties: EthereumProperties,
) {

    @Bean
    @ConditionalOnMissingBean(MonoEthereum::class)
    @ConditionalOnBean(MonoRpcTransport::class)
    fun ethereum(rpcTransport: MonoRpcTransport) = with(ethereumProperties) {
        if (cache.enabled) {
            CacheableMonoEthereum(
                transport = rpcTransport,
                expireAfter = cache.expireAfter,
                cacheMaxSize = cache.maxSize,
            )
        } else MonoEthereum(rpcTransport)
    }
}
