package com.rarible.ethereum.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

internal const val RARIBLE_ETHEREUM_PARITY = "rarible.ethereum.parity"

@ConfigurationProperties(RARIBLE_ETHEREUM_PARITY)
@ConstructorBinding
data class EthereumParityProperties(
    val httpUrl: String?,
    val requestTimeoutMs: Int = 10000,
    val readWriteTimeoutMs: Int = 10000,
    val maxFrameSize: Int = 1024 * 1024
)
