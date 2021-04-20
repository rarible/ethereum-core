package com.rarible.ethereum.monitoring

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.ethereum.domain.Blockchain
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.NestedConfigurationProperty

internal const val RARIBLE_MONITORING_STORAGE = "rarible.blockchain.monitoring"

@ConstructorBinding
@ConfigurationProperties(RARIBLE_MONITORING_STORAGE)
data class BlockchainMonitoringProperties(
    val blockchain: Blockchain,
    @field:NestedConfigurationProperty
    val workerProperties: DaemonWorkerProperties = DaemonWorkerProperties()
)