package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.domain.CheckedBlock
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.ethereum.listener.log.persist.RevertedLogStateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class RevertedLogsCheckJob(
    private val logEventRepository: LogEventRepository,
    private val blockRepository: BlockRepository,
    private val revertedLogStateRepository: RevertedLogStateRepository,
    logEventDescriptorHolder: LogEventDescriptorHolder,
    @Value("\${revertedLogsCheckJobInitBlockNumber:1}") private val initBlockNumber: Long
) {
    private val checkCollections = logEventDescriptorHolder.list.map { descriptor -> descriptor.collection }.toSet()

    @Scheduled(fixedRateString = "\${revertedLogsCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE * 5}}", initialDelay = DateUtils.MILLIS_PER_MINUTE)
    fun job() = runBlocking<Unit> {
        logger.info("Started reverted logs check job")

        try {
            val latestStateBlockNumber = blockRepository.findFirstByIdDesc().awaitFirstOrNull()?.id ?: run {
                logger.warn("Can't find any latest block")
                return@runBlocking
            }
            val lastCheckedBlockNumber = getLastCheckedBlockNumber()

            (lastCheckedBlockNumber until latestStateBlockNumber).forEach { checkBlockNumber ->
                val hasRevertedLog = coroutineScope {
                    checkCollections
                        .map { collection -> async { logEventRepository.hasRevertedLogEvent(collection, checkBlockNumber) } }
                        .awaitAll()
                        .any { it }
                }
                if (hasRevertedLog) {
                    val newStatus = BlockStatus.ERROR
                    blockRepository.updateBlockStatus(checkBlockNumber, newStatus).awaitFirstOrNull()
                    logger.info("Set block $checkBlockNumber to $newStatus status as it has reverted logs")
                }
                revertedLogStateRepository.save(CheckedBlock(checkBlockNumber))
            }
        } catch (e: Throwable) {
            logger.error("Failed to process reverted logs", e)
        } finally {
            logger.info("Finished reverted logs processing job")
        }
    }

    private suspend fun getLastCheckedBlockNumber(): Long {
        return revertedLogStateRepository.get()?.blockNumber ?: initBlockNumber
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(RevertedLogsCheckJob::class.java)
    }
}
