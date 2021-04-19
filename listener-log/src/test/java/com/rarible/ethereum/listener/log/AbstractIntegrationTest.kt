package com.rarible.ethereum.listener.log

import com.rarible.core.test.ext.EthereumTest
import com.rarible.core.test.ext.MongoCleanup
import com.rarible.core.test.ext.MongoTest
import com.rarible.ethereum.listener.log.mock.TestLogConfiguration
import com.rarible.rpc.domain.Word
import com.rarible.rpc.mono.WebClientTransport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.PropertySources
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.test.context.ContextConfiguration
import org.web3j.utils.Numeric
import reactor.core.publisher.Mono
import scalether.core.EthPubSub
import scalether.core.MonoEthereum
import scalether.domain.response.TransactionReceipt
import scalether.transaction.*
import scalether.transport.WebSocketPubSubTransport
import java.math.BigInteger

@EthereumTest
@MongoTest
@MongoCleanup
@SpringBootTest
@ContextConfiguration(classes = [TestLogConfiguration::class, EthereumConfigurationIntr::class])
class AbstractIntegrationTest {
    @Autowired
    protected lateinit var sender: MonoTransactionSender
    @Autowired
    protected lateinit var poller: MonoTransactionPoller
    @Autowired
    protected lateinit var ethereum: MonoEthereum
    @Autowired
    protected lateinit var mongo: ReactiveMongoOperations

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
    PropertySource(value = ["classpath:/ethereum.properties", "classpath:/ethereum-test.properties"], ignoreResourceNotFound = true)
)
class EthereumConfigurationIntr {
    @Value("\${parityUrls}")
    lateinit var ethereumUrl: String
    @Value("\${parityWebSocketUrls}")
    lateinit var ethereumWebsockerUrl: String
    @Value("\${ethereumPrivateKey}")
    lateinit var privateKey: String

    @Bean
    fun pubsub() =
        EthPubSub(WebSocketPubSubTransport(ethereumWebsockerUrl, 500000))

    @Bean
    fun ethereum() =
        MonoEthereum(WebClientTransport(ethereumUrl, MonoEthereum.mapper(), 10000, 10000))

    @Bean
    fun sender(ethereum: MonoEthereum) = MonoSigningTransactionSender(
        ethereum,
        MonoSimpleNonceProvider(ethereum),
        Numeric.toBigInt(privateKey),
        BigInteger.valueOf(8000000),
        MonoGasPriceProvider { Mono.just(BigInteger.ZERO) }
    )

    @Bean
    fun poller(ethereum: MonoEthereum) = MonoTransactionPoller(ethereum)
}
