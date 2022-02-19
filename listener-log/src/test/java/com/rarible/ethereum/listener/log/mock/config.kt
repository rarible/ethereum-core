package com.rarible.ethereum.listener.log.mock

import com.github.cloudyrock.spring.v5.EnableMongock
import com.rarible.ethereum.listener.log.EnableLogListeners
import com.rarible.contracts.test.erc20.TransferEvent
import com.rarible.contracts.test.erc1155.TransferSingleEvent
import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomWord
import com.rarible.ethereum.listener.log.LogEventDescriptor
import com.rarible.ethereum.listener.log.OnLogEventListener
import com.rarible.ethereum.listener.log.data.DummyData1
import com.rarible.ethereum.listener.log.data.DummyData2
import io.daonomic.rpc.domain.Word
import io.mockk.every
import io.mockk.mockk
import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono
import scalether.domain.Address
import scalether.domain.response.Log
import scalether.domain.response.Transaction

@EnableLogListeners(scanPackage = [TestLogConfiguration::class])
@EnableMongock
@EnableAutoConfiguration
class TestLogConfiguration {
    @Bean
    fun onErc20TransferEventListener1() : OnLogEventListener {
        return mockk {
            every { topics } returns listOf(TransferEvent.id(), TransferSingleEvent.id())
            every { onLogEvent(any()) } returns Mono.empty()
        }
    }

    @Bean
    fun onErc20TransferEventListener2() : OnLogEventListener {
        return mockk {
            every { topics } returns listOf(TransferSingleEvent.id(), TransferEvent.id())
            every { onLogEvent(any()) } returns Mono.empty()
        }
    }

    @Bean
    fun onOtherEventListener() : OnLogEventListener {
        return mockk {
            every { topics } returns listOf(TransferSingleEvent.id())
            every { onLogEvent(any()) } returns Mono.empty()
        }
    }

    @Bean
    fun logEventDescriptorWithDummyData1(): LogEventDescriptor<DummyData1>  {
        return object : LogEventDescriptor<DummyData1> {
            override val collection: String
                get() = "dummy_data1"

            override val topic: Word
                get() = Word.apply(randomWord())

            override fun convert(log: Log, transaction: Transaction, timestamp: Long, index: Int, totalLogs: Int): Publisher<DummyData1> {
                return Mono.empty()
            }

            override fun getAddresses(): Mono<Collection<Address>> {
                return Mono.just(listOf(randomAddress()))
            }
        }
    }

    @Bean
    fun logEventDescriptorWithDummyData2(): LogEventDescriptor<DummyData2>  {
        return object : LogEventDescriptor<DummyData2> {
            override val collection: String
                get() = "dummy_data2"

            override val topic: Word
                get() = Word.apply(randomWord())

            override fun convert(log: Log, transaction: Transaction, timestamp: Long, index: Int, totalLogs: Int): Publisher<DummyData2> {
                return Mono.empty()
            }

            override fun getAddresses(): Mono<Collection<Address>> {
                return Mono.just(listOf(randomAddress()))
            }
        }
    }
}