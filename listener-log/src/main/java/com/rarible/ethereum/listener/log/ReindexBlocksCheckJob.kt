package com.rarible.ethereum.listener.log

import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ReindexBlocksCheckJob(
    private val reindexBlockService: ReindexBlockService
) {
    @Scheduled(
        fixedRateString = "\${pendingBlocksCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE}}",
        initialDelayString = "\${pendingBlocksCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE}}"
    )
    fun job() {
        logger.info("started")
        try {
            reindexBlockService.indexPendingBlocks().block()
            logger.info("ended")
        } catch (e: Exception) {
            logger.error("error", e)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ReindexBlocksCheckJob::class.java)
    }
}