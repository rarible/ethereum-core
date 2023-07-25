package com.rarible.ethereum.listener.log

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.core.daemon.sequential.SequentialDaemonWorker
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.time.delay
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Duration

@Service
@ExperimentalCoroutinesApi
class ReindexBlocksCheckWorker(
    private val handler: ReindexBlockService,
    @Value("\${reindexBlocksJobEnabled:true}") private val reindexEnabled: Boolean,
    @Value("\${pendingBlocksCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE}}") private val checkInterval: Long,
    meterRegistry: MeterRegistry,
) : SequentialDaemonWorker(
    meterRegistry,
    DaemonWorkerProperties().run {
        val interval = Duration.ofMillis(checkInterval)
        copy(pollingPeriod = interval, errorDelay = interval)
    },
    "reindex-pending-error-blocks-worker"
) {
    override suspend fun handle() {
        logger.info("Started reindex pending/error blocks")
        try {
            handler.indexPendingBlocks().awaitFirstOrNull()
            logger.info("End reindex pending/error blocks")
        } catch (e: Throwable) {
            logger.error("Error pending block reindex", e)
        }
        delay(pollingPeriod)
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationStarted() {
        if (reindexEnabled) {
            logger.info("Starting pending/error block reindex worker: check interval $checkInterval ms")
            start()
        } else {
            logger.info("Pending/error block reindex worker is disabled")
        }
    }
}
