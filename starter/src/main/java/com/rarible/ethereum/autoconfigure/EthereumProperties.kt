package com.rarible.ethereum.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

internal const val RARIBLE_ETHEREUM = "rarible.ethereum"

@ConfigurationProperties(RARIBLE_ETHEREUM)
@ConstructorBinding
data class EthereumProperties(
    val httpUrl: String?,
    val websocketUrl: String?,
    val requestTimeoutMs: Int = 10000,
    val readWriteTimeoutMs: Int = 10000,
    val maxFrameSize: Int = 1024 * 1024,
    val retryMaxAttempts: Long = 5,
    val retryBackoffDelay: Long = 100,
    val cache: CacheProperties = CacheProperties()
)

data class CacheProperties(
    val enabled: Boolean = false,
    val expireAfter: Duration = Duration.ofMinutes(1),
    val maxSize: Long = 100,
)