package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.client.EthereumNode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

internal const val RARIBLE_ETHEREUM = "rarible.ethereum"

@ConfigurationProperties(RARIBLE_ETHEREUM)
@ConstructorBinding
data class EthereumProperties(
    val httpUrl: String?,
    val websocketUrl: String?,
    val httpUrls: List<String> = emptyList(),
    val websocketUrls: List<String> = emptyList(),
    val externalHttpUrls: List<String> = emptyList(),
    val externalWebsocketUrls: List<String> = emptyList(),
    @Deprecated("use httpUrls, websocketUrls")
    val nodes: List<EthereumNode> = emptyList(),
    @Deprecated("use externalHttpUrls, externalWebsocketUrls")
    val externalNodes: List<EthereumNode> = emptyList(),
    val requestTimeoutMs: Int = 10000,
    val readWriteTimeoutMs: Int = 10000,
    val maxFrameSize: Int = 1024 * 1024,
    val retryMaxAttempts: Long = 5,
    val retryBackoffDelay: Long = 100,
    val monitoringThreadInterval: Duration = Duration.ofSeconds(30),
    val cache: CacheProperties = CacheProperties(),
    val failoverEnabled: Boolean = true,
) {
    init {
        require(httpUrls.size == websocketUrls.size) {
            "httpUrls has different size ${httpUrls.size} from websocketUrls ${websocketUrls.size}"
        }
        require(externalHttpUrls.size == externalWebsocketUrls.size) {
            "externalHttpUrls has different size ${externalHttpUrls.size} from " +
                "externalWebsocketUrls ${externalWebsocketUrls.size}"
        }
    }
}

data class CacheProperties(
    val enabled: Boolean = false,
    val expireAfter: Duration = Duration.ofSeconds(10),
    val maxSize: Long = 100,
)
