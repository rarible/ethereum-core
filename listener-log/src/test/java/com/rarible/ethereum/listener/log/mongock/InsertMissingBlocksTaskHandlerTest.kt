package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.common.justOrEmpty
import com.rarible.ethereum.block.Blockchain
import com.rarible.ethereum.listener.log.block.SimpleBlock
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.daonomic.rpc.domain.Bytes
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.IllegalStateException

@ExperimentalStdlibApi
class InsertMissingBlocksTaskHandlerTest {

    private lateinit var simpleBlockchain: SimpleBlockchain

    private lateinit var knownBlocks: MutableMap<Long, BlockHead>
    private val blockRepository = mockk<BlockRepository>()

    @BeforeEach
    fun cleanupMocks() {
        clearMocks(blockRepository)
    }

    @Test
    fun `insert missing blocks - single window`() = runBlocking<Unit> {
        val totalBlocks = 100
        val realBlocks = randomBlockSequence().take(totalBlocks).toList()
        val knownBlocks = listOf(
            realBlocks.first(),
            realBlocks.last()
        )
        setupBlocks(realBlocks, knownBlocks)
        InsertMissingBlocksTaskHandler(blockRepository, simpleBlockchain).runLongTask(null, "").collect()
        verifyBlocksInserted(totalBlocks)
    }

    @Test
    fun `insert missing blocks - multiple windows`() = runBlocking<Unit> {
        val totalBlocks = 100
        val realBlocks = randomBlockSequence().take(totalBlocks).toList()
        val knownBlocks = buildList {
            add(realBlocks.first())
            for (i in 1..totalBlocks) {
                if (i % 10 == 5) {
                    addAll(realBlocks.subList(i, i + 5))
                }
            }
            add(realBlocks.last())
        }
        setupBlocks(realBlocks, knownBlocks)
        InsertMissingBlocksTaskHandler(blockRepository, simpleBlockchain).runLongTask(null, "").collect()
        verifyBlocksInserted(totalBlocks)
    }

    /**
     * Imitate that node returns inconsistent blocks: #1-30 and #50-100 are OK, but #31-#49 are garbage.
     * We must throw an exception if it happens.
     */
    @Test
    fun `insert missing blocks - if node returns some inconsistent result - throw exception`() = runBlocking<Unit> {
        val totalBlocks = 100
        val realBlocks = randomBlockSequence().take(totalBlocks).toList()
        val knownBlocks = buildList {
            addAll(realBlocks.take(30))
            // Blocks 20 - 50 are missing.
            addAll(realBlocks.drop(50))
        }
        val inconsistentNodeBlocks = buildList {
            addAll(realBlocks.take(30))
            // 20 garbage blocks
            addAll(randomBlockSequence().drop(30).take(20).toList())
            addAll(realBlocks.drop(50))
        }
        setupBlocks(inconsistentNodeBlocks, knownBlocks)
        assertThatThrownBy {
            runBlocking {
                InsertMissingBlocksTaskHandler(blockRepository, simpleBlockchain).runLongTask(null, "").collect()
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Found inconsistency in parent-child blocks")
    }

    @Test
    fun `insert missing blocks - last known block is from another fork - throw exception`() = runBlocking<Unit> {
        val totalBlocks = 100
        val realBlocks = randomBlockSequence().take(totalBlocks).toList()
        val forkedBlocks = randomBlockSequence().take(totalBlocks).toList()
        assertThat(realBlocks.first().hash).isNotEqualTo(forkedBlocks.first().hash)
        val knownBlocks = buildList {
            // Blocks #1-30 come from the previous fork.
            // Then 20 blocks are missing
            // Blocks #50-100 come from the real blockchain.
            // => Inconsistent window => we cannot insert it.
            addAll(forkedBlocks.take(30))
            addAll(realBlocks.drop(50))
        }
        setupBlocks(realBlocks, knownBlocks)
        assertThatThrownBy {
            runBlocking {
                InsertMissingBlocksTaskHandler(blockRepository, simpleBlockchain).runLongTask(null, "").collect()
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Block #30 recorded in the database has incorrect hash")
    }

    private suspend fun verifyBlocksInserted(totalBlocks: Int) {
        LongRange(1, totalBlocks.toLong()).forEach { blockNumber ->
            assertThat(blockRepository.findById(blockNumber)?.hash)
                .isEqualTo(simpleBlockchain.getBlock(blockNumber).awaitFirstOrNull()?.hash)
        }
    }

    private fun setupBlocks(realBlocks: List<SimpleBlock>, knownBlocks: List<SimpleBlock>) {
        this.knownBlocks = knownBlocks.associateBy { it.blockNumber }
            .mapValues { it.value.toBlockHead() }
            .toMutableMap()

        every { blockRepository.findFirstByIdAsc() } returns
                this.knownBlocks.keys.min()?.let { this.knownBlocks[it] }.justOrEmpty()
        every { blockRepository.findFirstByIdDesc() } returns
                this.knownBlocks.keys.max()?.let { this.knownBlocks[it] }.justOrEmpty()
        coEvery { blockRepository.findById(any()) } answers {
            val blockNumber = firstArg<Long>()
            this@InsertMissingBlocksTaskHandlerTest.knownBlocks[blockNumber]
        }
        coEvery { blockRepository.save(any()) } answers {
            val blockHead = firstArg<BlockHead>()
            this@InsertMissingBlocksTaskHandlerTest.knownBlocks[blockHead.id] = blockHead
            blockHead
        }
        simpleBlockchain = SimpleBlockchain(realBlocks)
    }
}

private fun randomBlockSequence(): Sequence<SimpleBlock> =
    generateSequence(
        SimpleBlock(
            hash = randomWordd(),
            parentHash = randomWordd(),
            number = 1,
            timestamp = 1
        )
    ) { previous ->
        SimpleBlock(
            hash = randomWordd(),
            parentHash = previous.hash,
            number = previous.number + 1,
            timestamp = previous.timestamp + 1
        )
    }

private fun SimpleBlock.toBlockHead() = BlockHead(number, hash, timestamp, BlockStatus.PENDING)

private class SimpleBlockchain(
    blocks: List<SimpleBlock>
) : Blockchain<SimpleBlock> {

    private val byNumber = blocks.associateBy { it.blockNumber }
    private val byHash = blocks.associateBy { it.blockHash }
    private val lastKnown = blocks.map { it.blockNumber }.max()

    override fun getLastKnownBlock(): Mono<Long> =
        Mono.justOrEmpty(lastKnown)

    override fun getBlock(hash: Bytes): Mono<SimpleBlock> =
        byHash[hash].let { Mono.justOrEmpty(it) }

    override fun getBlock(number: Long): Mono<SimpleBlock> =
        byNumber[number].let { Mono.justOrEmpty(it) }

    override fun listenNewBlocks(): Flux<SimpleBlock> = Flux.empty()
}
