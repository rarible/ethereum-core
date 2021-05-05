package com.rarible.ethereum.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

internal const val RARIBLE_CORE_ETHEREUM_DOMAIN_CONVERTER = "rarible.ethereum.converter"

@ConfigurationProperties(RARIBLE_CORE_ETHEREUM_DOMAIN_CONVERTER)
@ConstructorBinding
data class EthereumDomainConvertersProperties(
    val enabled: Boolean = false
)