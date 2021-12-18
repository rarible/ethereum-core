package com.rarible.ethereum.listener.log

import com.rarible.ethereum.autoconfigure.EthereumAutoConfiguration
import com.rarible.ethereum.autoconfigure.EthereumProperties
import io.daonomic.rpc.domain.Word
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
import org.web3j.utils.Numeric
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.response.TransactionReceipt
import scalether.transaction.*
import java.math.BigInteger

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
    fun testEthereum(ethereumProperties: EthereumProperties): MonoEthereum {
        val ethereum = EthereumAutoConfiguration(ethereumProperties).ethereum()
        return spyk(ethereum)
    }
}
