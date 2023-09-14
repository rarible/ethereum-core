package com.rarible.ethereum.client.cache

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.domain.Word
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.response.Block
import scalether.domain.response.Transaction
import java.math.BigInteger
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

@Suppress("ReactiveStreamsUnusedPublisher")
internal class CacheableMonoEthereumTest {
    private val transport = mockk<MonoRpcTransport>()
    private val cacheableMonoEthereum = CacheableMonoEthereum(
        delegate = MonoEthereum(transport),
        expireAfter = Duration.ofDays(1),
        cacheMaxSize = 100,
    )

    @Test
    fun `cache - ok`() = runBlocking<Unit> {
        val hash = randomWord()
        val block = mockk<Block<Transaction>>()

        every {
            transport.send<Block<Transaction>>(any(), any())
        } returns Mono.just(Response(1, block))

        val requests = (1..100).map {
            async { cacheableMonoEthereum.ethGetFullBlockByHash(hash).awaitFirst() }
        }.awaitAll()

        assertThat(requests).hasSize(100)
        assertThat(requests.all { it == block }).isTrue

        verify(exactly = 1) { transport.send<Block<Transaction>>(any(), any()) }
    }

    @Test
    fun `cache - ok, by block number`() = runBlocking<Unit> {
        val hash = randomWord()
        val number = BigInteger.TEN
        val block = mockk<Block<Transaction>>() {
            every { hash() } returns hash
        }
        every {
            transport.send<Block<Transaction>>(any(), any())
        } returns Mono.just(Response(1, block))

        cacheableMonoEthereum.ethGetFullBlockByNumber(number).awaitFirst()
        cacheableMonoEthereum.ethGetFullBlockByHash(hash).awaitFirst()

        verify(exactly = 1) { transport.send<Block<Transaction>>(any(), any()) }
    }

    @Test
    fun `cache with expired - ok`() = runBlocking<Unit> {
        val hash = randomWord()
        val expireAfter = Duration.ofMillis(100)

        val cacheableMonoEthereum = CacheableMonoEthereum(
            delegate = MonoEthereum(transport),
            expireAfter = expireAfter,
            cacheMaxSize = 100,
        )
        every {
            transport.send<Block<Transaction>>(any(), any())
        } returns Mono.just(Response(1, mockk<Block<Transaction>>()))

        cacheableMonoEthereum.ethGetFullBlockByHash(hash).awaitFirst()
        delay(expireAfter.multipliedBy(2).toMillis())
        cacheableMonoEthereum.ethGetFullBlockByHash(hash).awaitFirst()

        verify(exactly = 2) { transport.send<Block<Transaction>>(any(), any()) }
    }

    private fun randomWord(): Word {
        val hash = ByteArray(32)
        ThreadLocalRandom.current().nextBytes(hash)
        return Word.apply(hash)
    }
}
