package com.rarible.ethereum.monitoring

import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BlockchainMonitoringProperties::class)
class BlockchainMonitoringConfiguration(
    private val blockchainMonitoringProperties: BlockchainMonitoringProperties,
    private val meterRegistry: MeterRegistry,
    private val blockRepository: BlockRepository
) {
    @Bean
    fun blockchainMonitoringWorker() = BlockchainMonitoringWorker(
        properties = blockchainMonitoringProperties.workerProperties,
        blockchain = blockchainMonitoringProperties.blockchain,
        meterRegistry = meterRegistry,
        blockRepository = blockRepository
    )
}