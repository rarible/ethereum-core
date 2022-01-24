package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.domain.CheckedBlock
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.ethereum.listener.log.persist.RevertedLogStateRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import scalether.core.MonoEthereum

@Service
class RevertedLogsCheckJob(
    private val logEventRepository: LogEventRepository,
    private val blockRepository: BlockRepository,
    private val revertedLogStateRepository: RevertedLogStateRepository,
    private val logListenService: LogListenService,
    private val ethereum: MonoEthereum,
    logEventDescriptorHolder: LogEventDescriptorHolder,
    @Value("\${revertedLogsCheckJobInitBlockNumber:1}") private val initBlockNumber: Long,
    @Value("\${revertedLogsCheckJobBlockOffset:12}") private val offset: Long
) {
    private val checkCollections = logEventDescriptorHolder.list.map { descriptor -> descriptor.collection }.toSet()

    @Scheduled(fixedRateString = "\${revertedLogsCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE * 5}}", initialDelay = DateUtils.MILLIS_PER_MINUTE)
    fun job() = runBlocking<Unit> {
        logger.info("Started reverted logs and hash check job")

        try {
            val latestStateBlockNumber = blockRepository.findFirstByIdDesc().awaitFirstOrNull()?.id ?: run {
                logger.warn("Can't find any latest block")
                return@runBlocking
            }
            val lastCheckedBlockNumber = getLastCheckedBlockNumber()

            (lastCheckedBlockNumber until (latestStateBlockNumber - offset)).forEach { checkBlockNumber ->
                val block = blockRepository.findById(checkBlockNumber)
                val blockchainBlock = ethereum.ethGetBlockByNumber(checkBlockNumber.toBigInteger()).awaitFirst()

                val hasRevertedLog = coroutineScope {
                    checkCollections
                        .map { collection -> async { logEventRepository.hasRevertedLogEvent(collection, checkBlockNumber) } }
                        .awaitAll()
                        .any { it }
                }
                if (hasRevertedLog || blockchainBlock.hash() != block?.hash) {
                    val blockHead = BlockHead(
                        id = checkBlockNumber,
                        hash = blockchainBlock.hash(),
                        timestamp = blockchainBlock.timestamp().toLong(),
                        status = block?.status ?: BlockStatus.ERROR
                    )
                    logger.info("Block has incorrect hash or reverted logs, in the db: ${block?.id}, old hash: ${block?.hash}, new hash: ${blockchainBlock.hash()}, number: ${blockchainBlock.number().toLong()}")
                    blockRepository.save(blockHead)
                    logListenService.reindexBlock(blockHead).awaitFirstOrNull()
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
