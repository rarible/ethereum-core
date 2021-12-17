package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.task.TaskHandler
import com.rarible.ethereum.block.Blockchain
import com.rarible.ethereum.listener.log.block.SimpleBlock
import com.rarible.ethereum.listener.log.persist.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.util.retry.Retry
import java.time.Duration

/**
 * Background job that finds blocks in the database with wrong hashes.
 *
 * For now, it is only logging such blocks and does nothing.
 */
@Component
class RevertWrongBlocksTaskHandler(
    private val blockRepository: BlockRepository,
    private val blockchain: Blockchain<SimpleBlock>
) : TaskHandler<Long> {

    override val type: String
        get() = "REVERT_WRONG_BLOCKS"

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        return flow {
            val firstBlockId = blockRepository.findFirstByIdAsc().awaitFirstOrNull()?.id ?: return@flow
            val lastBlockId = blockRepository.findFirstByIdDesc().awaitFirstOrNull()?.id ?: return@flow
            val startBlockId = from ?: firstBlockId
            for (blockNumber in startBlockId..lastBlockId) {
                val block = blockRepository.findById(blockNumber)
                if (block == null) {
                    logger.warn("Missing block #$blockNumber")
                    continue
                }
                val simpleBlock = getSimpleBlock(blockNumber)
                if (block.hash != simpleBlock.hash) {
                    logger.warn("Wrong block #$blockNumber: expected hash = ${simpleBlock.hash}, actual hash = ${block.hash}")
                }
            }
            logger.info("Finished finding missing blocks from $startBlockId to $lastBlockId")
        }
    }

    private suspend fun getSimpleBlock(blockNumber: Long): SimpleBlock {
        return try {
            blockchain
                .getBlock(blockNumber)
                .retryWhen(Retry.backoff(10, Duration.ofMillis(100)))
                .awaitFirst()
        } catch (e: Exception) {
            logger.error("Failed to get block by $blockNumber", e)
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RevertWrongBlocksTaskHandler::class.java)
    }
}
