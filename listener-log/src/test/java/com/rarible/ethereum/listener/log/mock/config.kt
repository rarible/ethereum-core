package com.rarible.ethereum.listener.log.mock

import com.github.cloudyrock.spring.v5.EnableMongock
import com.rarible.ethereum.listener.log.EnableLogListeners
import com.rarible.contracts.test.erc20.TransferEvent
import com.rarible.contracts.test.erc1155.TransferSingleEvent
import com.rarible.ethereum.listener.log.OnLogEventListener
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono

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
}