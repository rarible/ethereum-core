package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.client.cache.CacheableMonoEthereum
import com.rarible.ethereum.client.monitoring.MonitoredEthereum
import com.rarible.ethereum.client.monitoring.MonitoringCallback
import io.daonomic.rpc.MonoRpcTransport
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import scalether.core.MonoEthereum

@Configuration
@ImportAutoConfiguration(EthereumTransportConfiguration::class)
@EnableConfigurationProperties(EthereumProperties::class)
class EthereumAutoConfiguration(
    private val ethereumProperties: EthereumProperties,
) {

    @Bean
    @ConditionalOnMissingBean(MonoEthereum::class)
    @ConditionalOnBean(MonoRpcTransport::class)
    fun ethereum(
        rpcTransport: MonoRpcTransport,
        @Autowired(required = false)
        monitoringCallback: MonitoringCallback?,
    ) = with(ethereumProperties) {
        val client = MonoEthereum(rpcTransport)
        val monitoredClient = if (monitoringCallback != null) {
            logger.info("Will use MonitoredEthereum")
            MonitoredEthereum(delegate = client, monitoringCallback = monitoringCallback)
        } else {
            client
        }
        if (cache.enabled) {
            logger.info("Will use CacheableMonoEthereum")
            CacheableMonoEthereum(
                delegate = monitoredClient,
                expireAfter = cache.expireAfter,
                cacheMaxSize = cache.maxSize,
            )
        } else {
            monitoredClient
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EthereumAutoConfiguration::class.java)
    }
}
