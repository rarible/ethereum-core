package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.client.EthereumNode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.http.MediaType
import java.time.Duration

internal const val RARIBLE_ETHEREUM = "rarible.ethereum"

@ConfigurationProperties(RARIBLE_ETHEREUM)
@ConstructorBinding
data class EthereumProperties(
    val httpUrl: String?,
    val nodes: List<EthereumNode> = emptyList(),
    val externalNodes: List<EthereumNode> = emptyList(),
    val reconciliationNodes: List<EthereumNode> = emptyList(),
    val requestTimeoutMs: Int = 10000,
    val readWriteTimeoutMs: Int = 10000,
    val maxFrameSize: Int = 1024 * 1024,
    val retryMaxAttempts: Long = 5,
    val retryBackoffDelay: Long = 100,
    val monitoringThreadInterval: Duration = Duration.ofSeconds(30),
    val cache: CacheProperties = CacheProperties(),
    val failoverEnabled: Boolean = true,
    val maxBlockDelay: Duration = Duration.ofMinutes(2),
    val allowTransactionsWithoutHash: Boolean = false,
    val mediaType: MediaType = MediaType.APPLICATION_JSON,
    val reconciliationMediaType: MediaType = MediaType.APPLICATION_JSON,
)

data class CacheProperties(
    val enabled: Boolean = false,
    val expireAfter: Duration = Duration.ofSeconds(10),
    val maxSize: Long = 100,
    val enableCacheByNumber: Boolean = false,
    val blockByNumberCacheExpireAfter: Duration = Duration.ofSeconds(10)
)
