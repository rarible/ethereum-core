package com.rarible.ethereum.listener.log.mongock

import com.rarible.core.task.TaskHandler
import com.rarible.ethereum.listener.log.LogEventDescriptorHolder
import com.rarible.ethereum.listener.log.LogListenService
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import scalether.core.MonoEthereum

@Component
class InsertMissingBlockLogsTaskHandler(
    private val blockRepository: BlockRepository,
    private val logEventDescriptorHolder: LogEventDescriptorHolder,
    private val logEventRepository: LogEventRepository,
    private val logListenService: LogListenService,
    private val ethereum: MonoEthereum
) : TaskHandler<Long> {

    override val type: String
        get() = INSERT_MISSING_BLOCK_LOGS

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        return flow {
            val checkCollections = getCheckCollection(param)
            if (checkCollections.isEmpty()) {
                logger.warn("Can't determine collections to check, param=$param")
                return@flow
            }
            val fromBlock = from ?: blockRepository.findFirstByIdDesc().awaitFirstOrNull()?.id ?: return@flow
            logger.info("Start missing logs task with $from block")

            (fromBlock downTo 1).forEach { checkBlockNumber ->
                val noLogs = coroutineScope {
                    checkCollections
                        .map { async { logEventRepository.countLogsByBlockNumber(it, checkBlockNumber).awaitFirst() } }
                        .awaitAll()
                        .any { it == 0L }
                }
                if (noLogs) {
                    val block = blockRepository.findById(checkBlockNumber)
                    val blockchainBlock = ethereum.ethGetBlockByNumber(checkBlockNumber.toBigInteger()).awaitFirst()
                    val isSuccessBlock = (block?.status ?: BlockStatus.SUCCESS) == BlockStatus.SUCCESS

                    if (isSuccessBlock) {
                        val blockHead = BlockHead(
                            id = checkBlockNumber,
                            hash = blockchainBlock.hash(),
                            timestamp = blockchainBlock.timestamp().toLong(),
                            status = BlockStatus.SUCCESS
                        )
                        logger.info("Found a block $checkBlockNumber with zero logs, try to reindex it")
                        logListenService.reindexBlock(blockHead).awaitFirstOrNull()
                    }
                }
                emit(checkBlockNumber)
            }
        }
    }

    private fun getCheckCollection(param: String): List<String> {
        val paramCollections = param.split(",").map { it.trim() }
        val knownCollections = logEventDescriptorHolder.list.map { it.collection }

        paramCollections.forEach { collection ->
            if (knownCollections.none { it == collection }) {
                throw IllegalArgumentException("Can't recognize collection $collection, expected one of $knownCollections")
            }
        }
        return paramCollections
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InsertMissingBlockLogsTaskHandler::class.java)
        const val INSERT_MISSING_BLOCK_LOGS = "INSERT_MISSING_BLOCK_LOGS"
    }
}
