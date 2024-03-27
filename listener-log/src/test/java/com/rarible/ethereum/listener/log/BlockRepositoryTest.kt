package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.daonomic.rpc.domain.WordFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class BlockRepositoryTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var blockRepository: BlockRepository

    @Test
    fun findBlocksWorks() = runBlocking<Unit> {
        val block1 = BlockHead(100, WordFactory.create(), 1)
        val block2 = BlockHead(101, WordFactory.create(), 1)
        mongo.save(block1).awaitFirst()
        mongo.save(block2).awaitFirst()

        assertThat(
            blockRepository.findBlocks(100, 101).collectList().awaitFirst().map { it.id }
        ).containsExactly(block1.id)

        assertThat(
            blockRepository.findBlocks(null, null).collectList().awaitFirst().map { it.id }
        ).contains(block1.id, block2.id)

        assertThat(
            blockRepository.findBlocks(101, null).collectList().awaitFirst().map { it.id }
        ).contains(block2.id)

        assertThat(
            blockRepository.findBlocks(null, 101).collectList().awaitFirst().map { it.id }
        ).contains(block1.id)
    }
}
