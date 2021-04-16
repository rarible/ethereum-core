package com.rarible.ethereum.block

import com.rarible.rpc.domain.Binary
import com.rarible.rpc.domain.BinaryFactory
import com.rarible.rpc.domain.Bytes
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
}

class TestBlockchain(
    blocks: List<TestBlock>,
    private val events: Flux<TestBlock>
) : Blockchain<TestBlock> {

    private val byNumber = blocks.map { it.blockNumber to it }.toMap()
    private val byHash = blocks.map {it.blockHash as Bytes to it}.toMap()
    private val lastKnown = blocks.map { it.blockNumber }.max()

    override fun getLastKnownBlock(): Mono<Long> =
        Mono.justOrEmpty(lastKnown)

    override fun getBlock(hash: Bytes): Mono<TestBlock> =
        Mono.just(byHash.getValue(hash))

    override fun getBlock(number: Long): Mono<TestBlock> =
        Mono.just(byNumber.getValue(number))

    override fun listenNewBlocks(): Flux<TestBlock> = events
}