package com.rarible.ethereum.block

import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.BinaryFactory
import io.daonomic.rpc.domain.Bytes
import io.daonomic.rpc.domain.Word
import io.daonomic.rpc.domain.WordFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class BlockListenServiceTest {
    @Test
    fun startStateNoReorg() {
        val hash1 = BinaryFactory.create(32)
        val state = TestState(mapOf(1L to hash1))
        val blockchain = TestBlockchain(listOf(TestBlock(1L, hash1, BinaryFactory.create(32))), Flux.empty())

        val service = BlockListenService(state, blockchain)
        StepVerifier.create(service.listen())
            .verifyComplete()

        assert(state.blocks[1] == hash1)
        assert(state.blocks.size == 1)
    }

    @Test
    fun absentBlocks() {
        val block1 = TestBlock(1, BinaryFactory.create(32), BinaryFactory.create(32))
        val block2 = TestBlock(2, BinaryFactory.create(32), block1.blockHash)
        val block3 = TestBlock(3, BinaryFactory.create(32), block2.blockHash)
        val state = TestState(mapOf(1L to block1.blockHash))
        val blockchain = TestBlockchain(listOf(block1, block2), Flux.just(block3))

        val service = BlockListenService(state, blockchain)
        StepVerifier.create(service.listen())
            .expectNext(BlockEvent(block2))
            .expectNext(BlockEvent(block3))
            .verifyComplete()

        assert(state.blocks[1] == block1.blockHash)
        assert(state.blocks[2] == block2.blockHash)
        assert(state.blocks[3] == block3.blockHash)
        assert(state.blocks.size == 3)
    }

    @Test
    fun reorgLast() {
        val hash1 = BinaryFactory.create(32)
        val hash2 = BinaryFactory.create(32)
        val hash2New = BinaryFactory.create(32)
        val block2New = TestBlock(2, hash2New, hash1)
        val block3 = TestBlock(3, BinaryFactory.create(32), hash2New)

        val state = TestState(mapOf(1L to hash1, 2L to hash2))
        val blockchain = TestBlockchain(listOf(TestBlock(1, hash1, BinaryFactory.create(32)), block2New), Flux.just(block3))

        val service = BlockListenService(state, blockchain)
        StepVerifier.create(service.listen())
            .expectNext(BlockEvent(block2New, BlockInfo(hash2, 2)))
            .expectNext(BlockEvent(block3))
            .verifyComplete()

        assert(state.blocks[1] == hash1)
        assert(state.blocks[2] == hash2New)
        assert(state.blocks[3] == block3.blockHash)
        assert(state.blocks.size == 3)
    }

    @Test
    fun reorgStackOverflow() {
        val root = WordFactory.create()
        val testNumber = 10000
        val hashes1 = listOf(root) + (1..testNumber).map { WordFactory.create() }
        val hashes2 = listOf(root) + (1..testNumber).map { WordFactory.create() }

        val blocks = hashes2.zipWithNext().withIndex().flatMap { (idx, pair) ->
            if (idx == 0) {
                listOf(
                    TestBlock(0, pair.first.toBinary(), WordFactory.create().toBinary()),
                    TestBlock(1, pair.second.toBinary(), pair.first.toBinary())
                )
            } else {
                listOf(TestBlock(idx.toLong() + 1, pair.second.toBinary(), pair.first.toBinary()))
            }
        }

        val state = TestState.create(hashes1)
        val blockchain =
            TestBlockchain(blocks, Flux.just(blocks.last()))

        val service = BlockListenService(state, blockchain)
        val result = service.listen().collectList().block()!!
        assertEquals(result.size, testNumber)
        assertEquals(result.first().block.blockNumber, 1)
        assertEquals(result.last().block.blockNumber, testNumber.toLong())
    }

    @Test
    fun startNoState() {
        val testBlock = TestBlock(1, BinaryFactory.create(32), BinaryFactory.create(32))
        val state = TestState()
        val blockchain = TestBlockchain(listOf(), Flux.just(testBlock))

        val service = BlockListenService(state, blockchain)
        StepVerifier.create(service.listen())
            .expectNext(BlockEvent(testBlock))
            .verifyComplete()

        assert(state.blocks[1] == testBlock.blockHash)
        assert(state.blocks.size == 1)
    }
}

data class TestBlock(
    private val number: Long,
    private val hash: Binary,
    private val parentHash: Binary
) : Block {
    override val blockNumber: Long
        get() = number
    override val blockHash: Binary
        get() = hash
    override val parentBlockHash: Binary
        get() = parentHash
}

class TestState(
    blocks: Map<Long, Bytes> = emptyMap()
) : BlockState<TestBlock> {
    val blocks: MutableMap<Long, Bytes> = blocks.toMutableMap()

    override fun getLastKnownBlock(): Mono<Long> =
        Mono.justOrEmpty(blocks.keys.max())

    override fun getBlockHash(number: Long): Mono<Bytes> =
        blocks[number]?.let { Mono.just(it) } ?: Mono.empty()

    override fun saveKnownBlock(block: TestBlock): Mono<Void> {
        blocks[block.blockNumber] = block.blockHash
        return Mono.empty()
    }

    companion object {
        fun create(hashes: List<Word>): TestState {
            val map = hashes.withIndex().associateBy(
                { it.index.toLong() },
                { it.value as Bytes }
            )
            return TestState(map)
        }
    }
}

class TestBlockchain(
    blocks: List<TestBlock>,
    private val events: Flux<TestBlock>
) : Blockchain<TestBlock> {

    private val byNumber = blocks.associateBy { it.blockNumber }
    private val byHash = blocks.associateBy { it.blockHash as Bytes }
    private val lastKnown = blocks.map { it.blockNumber }.max()

    override fun getLastKnownBlock(): Mono<Long> =
        Mono.justOrEmpty(lastKnown)

    override fun getBlock(hash: Bytes): Mono<TestBlock> =
        byHash[hash].let { Mono.justOrEmpty(it) }

    override fun getBlock(number: Long): Mono<TestBlock> =
        byNumber[number].let { Mono.justOrEmpty(it) }

    override fun listenNewBlocks(): Flux<TestBlock> = events
}