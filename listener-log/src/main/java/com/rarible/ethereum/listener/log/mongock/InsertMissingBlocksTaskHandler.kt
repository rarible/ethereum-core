package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.task.TaskHandler
import com.rarible.ethereum.block.Blockchain
import com.rarible.ethereum.listener.log.block.SimpleBlock
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.util.retry.Retry
import java.time.Duration

@Component
class InsertMissingBlocksTaskHandler(
    private val blockRepository: BlockRepository,
    private val blockchain: Blockchain<SimpleBlock>
) : TaskHandler<Long> {

    override val type: String
        get() = TOPIC

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        return flow {
            val firstBlockId = blockRepository.findFirstByIdAsc().awaitFirstOrNull()?.id ?: return@flow
            val lastBlockId = blockRepository.findFirstByIdDesc().awaitFirstOrNull()?.id ?: return@flow
            val startBlockId = from ?: firstBlockId
            logger.info("Processing missing blocks from $startBlockId (first = $firstBlockId) to $lastBlockId")
            var lastSeenBlock: BlockHead? = null
            val windowOfMissingIds = arrayListOf<Long>()
            for (blockNumber in startBlockId..lastBlockId) {
                val block = blockRepository.findById(blockNumber)
                if (block == null) {
                    windowOfMissingIds += blockNumber
                    continue
                }
                if (windowOfMissingIds.isNotEmpty()) {
                    checkNotNull(lastSeenBlock)
                    val windowStartBlock = getSimpleBlockVerifying(lastSeenBlock)
                    val windowEndBlock = getSimpleBlockVerifying(block)
                    process(windowOfMissingIds, windowStartBlock, windowEndBlock)
                    windowOfMissingIds.clear()
                }
                emit(blockNumber)
                lastSeenBlock = block
            }
            check(windowOfMissingIds.isEmpty())
            logger.info("Inserted all missing blocks from $startBlockId to $lastBlockId")
        }
    }

    private suspend fun process(
        windowOfMissingIds: List<Long>,
        windowStartBlock: SimpleBlock,
        windowEndBlock: SimpleBlock
    ) {
        val blocksRangeMessage = "from ${windowOfMissingIds.first()} to ${windowOfMissingIds.last()}, " +
                "previous block = $windowStartBlock, next block = $windowEndBlock"
        logger.info("Processing window of missing IDs $blocksRangeMessage")
        val blocks = windowOfMissingIds.map { getSimpleBlock(it) }
        verifyConsistency(blocks, windowStartBlock, windowEndBlock)
        for (block in blocks) {
            logger.info("Inserting missing block #${block.blockNumber} as PENDING: $block")
            val blockHead = BlockHead(block.number, block.hash, block.timestamp, status = BlockStatus.PENDING)
            blockRepository.save(blockHead)
        }
    }

    /**
     * Make sure that the hash <-> parent hash  of all subsequent blocks do match.
     */
    private fun verifyConsistency(
        blocks: List<SimpleBlock>,
        windowStartBlock: SimpleBlock,
        windowEndBlock: SimpleBlock
    ) {
        fun verify(previous: SimpleBlock, next: SimpleBlock) {
            if (previous.hash != next.parentHash) {
                throw IllegalStateException("Found inconsistency in parent-child blocks previous = $previous, next = $next")
            }
        }
        for ((index, block) in blocks.withIndex()) {
            val previous = if (index == 0) windowStartBlock else blocks[index - 1]
            verify(previous, block)
        }
        verify(blocks.last(), windowEndBlock)
    }

    private suspend fun getSimpleBlockVerifying(blockHead: BlockHead): SimpleBlock {
        val simpleBlock = getSimpleBlock(blockHead.id)
        if (blockHead.hash != simpleBlock.blockHash) {
            throw IllegalStateException(
                "Block #${blockHead.id} recorded in the database has incorrect hash, " +
                        "actual = $blockHead," +
                        "expected = $simpleBlock"
            )
        }
        return simpleBlock
    }

    private suspend fun getSimpleBlock(blockNumber: Long): SimpleBlock {
        return try {
            blockchain
                .getBlock(blockNumber)
                .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(100)))
                .awaitFirst()
        } catch (e: Exception) {
            logger.error("Failed to get block by $blockNumber", e)
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InsertMissingBlocksTaskHandler::class.java)
        const val TOPIC = "INSERT_MISSING_BLOCKS"
    }
}
