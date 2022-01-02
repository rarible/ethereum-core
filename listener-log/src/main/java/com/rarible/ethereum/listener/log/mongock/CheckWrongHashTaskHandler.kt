package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.task.TaskHandler
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import scalether.core.MonoEthereum

@Component
class CheckWrongHashTaskHandler(
    private val blockRepository: BlockRepository,
    private val ethereum: MonoEthereum,
    @Value("\${ethereumCheckWrongHashDelay:10000}") private val delay: Long
) : TaskHandler<Long> {

    override val type: String
        get() = TYPE

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        val start = param.toLong()
        return blockRepository.findBlocks(start, from).asFlow().map {
            checkBlockHash(it)
            it.id
        }
    }

    private suspend fun checkBlockHash(block: BlockHead) {
        logger.info("Checking if block saved with incorrect block hash: ${block.id}")
        val bb = ethereum.ethGetBlockByNumber(block.id.toBigInteger()).awaitFirst()
        if (bb.hash() != block.hash) {
            logger.info("Block has incorrect hash in the db: ${block.id}, old hash: ${block.hash}, new hash: ${bb.hash()}")
            blockRepository.save(block.copy(status = BlockStatus.ERROR, hash = bb.hash(), timestamp = bb.timestamp().toLong()))
            delay(delay)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CheckWrongHashTaskHandler::class.java)
        const val TYPE: String = "CHECK_WRONG_HASH"
    }
}
