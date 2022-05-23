package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.test.data.randomLong
import com.rarible.ethereum.listener.log.LogEventDescriptor
import com.rarible.ethereum.listener.log.LogEventDescriptorHolder
import com.rarible.ethereum.listener.log.LogListenService
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import io.daonomic.rpc.domain.Word
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.response.Block

@Suppress("ReactiveStreamsUnusedPublisher")
internal class InsertMissingBlockLogsTaskHandlerTest {
    private val collection1 = "test1"
    private val collection2 = "testw"
    private val logEventDescriptor1 = mockk<LogEventDescriptor<*>> {
        every { collection } returns collection1
    }
    private val logEventDescriptor2 = mockk<LogEventDescriptor<*>> {
        every { collection } returns collection2
    }
    private val logEventDescriptorHolder = mockk<LogEventDescriptorHolder> {
        every { list } returns listOf(logEventDescriptor1, logEventDescriptor2)
    }
    private val blockRepository = mockk<BlockRepository>()
    private val logEventRepository = mockk<LogEventRepository>()
    private val logListenService = mockk<LogListenService>()
    private val ethereum = mockk<MonoEthereum>()

    private val handler = InsertMissingBlockLogsTaskHandler(
        blockRepository,
        logEventDescriptorHolder,
        logEventRepository,
        logListenService,
        ethereum
    )

    @Test
    fun `should not reindex block if it has logs`() = runBlocking<Unit> {
        val collections = "$collection1,$collection2"
        val blockHead = BlockHead(
            id = 5,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        every { blockRepository.findFirstByIdDesc() } returns Mono.just(blockHead)
        every { logEventRepository.countLogsByBlockNumber(collection1, any()) } returns Mono.just(1L)
        every { logEventRepository.countLogsByBlockNumber(collection2, any()) } returns Mono.just(1L)
        val checkedBlocks = handler.runLongTask(null,  collections).toList()
        assertThat(checkedBlocks).containsExactly(5L, 4L, 3L, 2L, 1L)
        verify(exactly = 0) { logListenService.reindexBlock(any()) }
    }

    @Test
    fun `should reindex blocks with no logs`() = runBlocking<Unit> {
        val collections = "$collection1,$collection2"
        val blockHead3 = BlockHead(
            id = 3,
            hash = randomWordd(),
            timestamp = randomLong(),
            status = BlockStatus.SUCCESS
        )
        val block3 = mockk<Block<Word>> {
            every { hash() } returns blockHead3.hash
            every { timestamp() } returns blockHead3.timestamp.toBigInteger()
        }
        val blockHead4 = BlockHead(
            id = 4,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        val block4 = mockk<Block<Word>> {
            every { hash() } returns blockHead4.hash
            every { timestamp() } returns blockHead4.timestamp.toBigInteger()
        }
        every { logEventRepository.countLogsByBlockNumber(collection1, any()) } returns Mono.just(1L)
        every { logEventRepository.countLogsByBlockNumber(collection1, 3) } returns Mono.just(0L)

        every { logEventRepository.countLogsByBlockNumber(collection2, any()) } returns Mono.just(1L)
        every { logEventRepository.countLogsByBlockNumber(collection2, 4) } returns Mono.just(0L)

        coEvery { blockRepository.findById(3L) } returns blockHead3
        coEvery { blockRepository.findById(4L) } returns blockHead4

        every { ethereum.ethGetBlockByNumber(3.toBigInteger()) } returns Mono.just(block3)
        every { ethereum.ethGetBlockByNumber(4.toBigInteger()) } returns Mono.just(block4)
        every { logListenService.reindexBlock(blockHead3) } returns Mono.empty()
        every { logListenService.reindexBlock(blockHead4) } returns Mono.empty()

        val checkedBlocks = handler.runLongTask(5,  collections).toList()
        assertThat(checkedBlocks).hasSize(5)
        verify(exactly = 2) { logListenService.reindexBlock(any()) }

        verify(exactly = 1) { logListenService.reindexBlock(blockHead3) }
        verify(exactly = 1) { logListenService.reindexBlock(blockHead4) }
    }

    @Test
    fun `should not reindex if error block`() = runBlocking<Unit> {
        val collections = collection1
        val blockHead3 = BlockHead(
            id = 3,
            hash = randomWordd(),
            timestamp = randomLong(),
            status = BlockStatus.ERROR
        )
        val block3 = mockk<Block<Word>> {
            every { hash() } returns blockHead3.hash
            every { timestamp() } returns blockHead3.timestamp.toBigInteger()
        }
        every { logEventRepository.countLogsByBlockNumber(collection1, any()) } returns Mono.just(1L)
        every { logEventRepository.countLogsByBlockNumber(collection1, 3) } returns Mono.just(0L)

        coEvery { blockRepository.findById(3L) } returns blockHead3
        every { ethereum.ethGetBlockByNumber(3.toBigInteger()) } returns Mono.just(block3)

        val checkedBlocks = handler.runLongTask(5,  collections).toList()
        assertThat(checkedBlocks).hasSize(5)
        verify(exactly = 0) { logListenService.reindexBlock(any()) }
    }
}