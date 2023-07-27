package com.rarible.ethereum.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import scalether.core.MonoEthereum
import scalether.transaction.ReadOnlyMonoTransactionSender

@ConditionalOnBean(MonoEthereum::class)
@AutoConfigureAfter(EthereumAutoConfiguration::class)
@ConditionalOnProperty(prefix = RARIBLE_CORE_ETHEREUM_READ_ONLY_TRANSACTION_SENDER, name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(ReadOnlyTransactionSenderProperties::class)
class ReadOnlyTransactionSenderAutoConfiguration(
    private val ethereum: MonoEthereum,
    private val properties: ReadOnlyTransactionSenderProperties
) {
    @Bean
    @ConditionalOnMissingBean
    fun readOnlyMonoTransactionSender(): ReadOnlyMonoTransactionSender {
        return ReadOnlyMonoTransactionSender(ethereum, properties.fromAddress)
    }
}
