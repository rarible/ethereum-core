package com.rarible.ethereum.listener.log

import com.rarible.core.task.TaskService
import com.rarible.ethereum.listener.log.mongock.CheckWrongHashTaskHandler
import com.rarible.ethereum.listener.log.persist.BlockRepository
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ReindexBlocksCheckJob(
    private val reindexBlockService: ReindexBlockService,
    private val blockRepository: BlockRepository,
    private val taskService: TaskService,
    @Value("\${reindexBlocksJobEnabled:true}") private val reindexBlocksJobEnabled: Boolean,
    @Value("\${checkWrongHashBlocksJobEnabled:true}") private val checkWrongHashBlocksJobEnabled: Boolean,
    @Value("\${checkWrongHashBlocksSuffixSize:1000}") private val checkWrongHashBlocksSuffixSize: Long
) {
    @Scheduled(
        fixedDelayString = "\${pendingBlocksCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE}}",
        initialDelayString = "\${pendingBlocksCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE}}"
    )
    fun job() {
        if (!reindexBlocksJobEnabled) {
            return
        }
        logger.info("Started reindex pending blocks")
        try {
            reindexBlockService.indexPendingBlocks().block()
            logger.info("End reindex pending blocks")
        } catch (e: Throwable) {
            logger.error("Error pending block reindex", e)
        }
    }

    @Scheduled(
        fixedDelayString = "\${checkWrongHashBlocksJobInterval:${DateUtils.MILLIS_PER_HOUR}}",
        initialDelayString = "\${checkWrongHashBlocksJobInterval:${DateUtils.MILLIS_PER_HOUR}}"
    )
    fun checkWrongHashBlocksJob() {
        if (!checkWrongHashBlocksJobEnabled) {
            return
        }
        val lastBlockNumber = blockRepository.findFirstByIdDesc().block()?.id ?: return
        val start = lastBlockNumber - checkWrongHashBlocksSuffixSize
        logger.info("Scheduling a task to re-check wrong hash blocks starting from block number $start")
        taskService.runTask(CheckWrongHashTaskHandler.TYPE, start.toString(), sample = 1)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ReindexBlocksCheckJob::class.java)
    }
}
