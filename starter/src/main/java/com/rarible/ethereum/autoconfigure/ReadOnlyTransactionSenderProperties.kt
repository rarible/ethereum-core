package com.rarible.ethereum.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import scalether.domain.Address

internal const val RARIBLE_CORE_ETHEREUM_READ_ONLY_TRANSACTION_SENDER = "rarible.ethereum.read-only-transaction-sender"

@ConfigurationProperties(RARIBLE_CORE_ETHEREUM_READ_ONLY_TRANSACTION_SENDER)
@ConstructorBinding
data class ReadOnlyTransactionSenderProperties(
    val enabled: Boolean = false,
    val fromAddress: Address = Address.ZERO()
)