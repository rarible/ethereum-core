package com.rarible.ethereum.listener.log

import com.rarible.ethereum.autoconfigure.EthereumAutoConfiguration
import com.rarible.ethereum.autoconfigure.EthereumProperties
import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Word
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.PropertySources
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.web3jold.utils.Numeric
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.response.TransactionReceipt
import scalether.transaction.MonoSigningTransactionSender
import scalether.transaction.MonoSimpleNonceProvider
import scalether.transaction.MonoTransactionPoller
import scalether.transaction.MonoTransactionSender
import java.math.BigInteger
import java.time.Instant

abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var sender: MonoTransactionSender

    @Autowired
    protected lateinit var poller: MonoTransactionPoller

    @Autowired
    protected lateinit var ethereum: MonoEthereum

    @Autowired
    protected lateinit var mongo: ReactiveMongoOperations

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    protected fun TransactionReceipt.getTimestamp(): Instant =
        Instant.ofEpochSecond(ethereum.ethGetFullBlockByHash(blockHash()).map { it.timestamp() }.block()!!.toLong())

    private fun Mono<Word>.waitReceipt(): TransactionReceipt {
        val value = this.block()
        require(value != null) { "txHash is null" }
        return ethereum.ethGetTransactionReceipt(value).block()!!.get()
    }

    protected fun Mono<Word>.verifyError(): TransactionReceipt {
        val receipt = waitReceipt()
        assertFalse(receipt.success())
        return receipt
    }

    protected fun Mono<Word>.verifySuccess(): TransactionReceipt {
        val receipt = waitReceipt()
        assertTrue(receipt.success())
        return receipt
    }
}

@Configuration
@PropertySources(
    PropertySource(
        value = ["classpath:/ethereum.properties", "classpath:/ethereum-test.properties"],
        ignoreResourceNotFound = true
    )
)
class EthereumConfigurationIntr {
    @Value("\${ethereumPrivateKey}")
    lateinit var privateKey: String

    @Bean
    fun sender(ethereum: MonoEthereum) = MonoSigningTransactionSender(
        ethereum,
        MonoSimpleNonceProvider(ethereum),
        Numeric.toBigInt(privateKey),
        BigInteger.valueOf(8000000),
        { Mono.just(BigInteger.ZERO) }
    )

    @Bean
    fun poller(ethereum: MonoEthereum) = MonoTransactionPoller(ethereum)

    @Bean
    fun testEthereum(ethereumProperties: EthereumProperties, monoRpcTransport: MonoRpcTransport): MonoEthereum {
        val ethereum = EthereumAutoConfiguration(ethereumProperties).ethereum(monoRpcTransport)
        return spyk(ethereum)
    }

    @Bean
    fun testMeterRegistry(): MeterRegistry {
        return SimpleMeterRegistry()
    }
}
