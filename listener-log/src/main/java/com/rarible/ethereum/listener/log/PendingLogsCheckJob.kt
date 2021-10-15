package com.rarible.ethereum.listener.log

import com.rarible.core.common.retryOptimisticLock
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.domain.NewBlockEvent
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.ethereum.log.LogEventsListener
import io.daonomic.rpc.domain.Word
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import scalether.core.MonoEthereum
import scalether.domain.response.Block
import java.time.Duration
import java.time.Instant

@Service
class PendingLogsCheckJob(
    private val logEventRepository: LogEventRepository,
    descriptors: List<LogEventDescriptor<*>>,
    private val ethereum: MonoEthereum,
    private val logListenService: LogListenService,
    private val logEventsListeners: List<LogEventsListener>? = null,
    @Value("\${pendingLogsProcessingEnabled:true}") private val pendingLogsProcessingEnabled: Boolean
) {
    private val collections = descriptors.map { it.collection }.toSet()

    @Scheduled(fixedRateString = "\${pendingLogsCheckJobInterval:${DateUtils.MILLIS_PER_MINUTE * 10}}", initialDelay = DateUtils.MILLIS_PER_MINUTE)
    fun job() {
        if (!pendingLogsProcessingEnabled) {
            return
        }
        logger.info("Started logs processing job")
        try {
            collections.toFlux()
                .flatMap { collection ->
                    logEventRepository.findPendingLogs(collection)
                        .filter { it.createdAt + SAFE_PENDING_BLOCK_PERIOD <= Instant.now() }
                        .flatMap { processLog(collection, it) }
                }
                .collectList()
                .flatMap { logsAndBlocks ->
                    val droppedLogs = logsAndBlocks.mapNotNull { it.first }
                    val newBlocks = logsAndBlocks.mapNotNull { it.second }.distinctBy { it.hash() }
                    Mono.`when`(
                        onDroppedLogs(droppedLogs),
                        onNewBlocks(newBlocks)
                    )
                }
                .block()
        } catch (e: Throwable) {
            logger.error("Failed to process pending logs", e)
        } finally {
            logger.info("Finished pending logs processing job")
        }
    }

    private fun onDroppedLogs(droppedLogs: List<LogEvent>): Mono<Void> =
        Flux.fromIterable(logEventsListeners ?: emptyList())
            .flatMap { logEventsListener ->
                logEventsListener.postProcessLogs(droppedLogs).onErrorResume { th ->
                    logger.error("caught exception while onDroppedLogs logs of listener: " + logEventsListener.javaClass, th)
                    Mono.empty()
                }
            }.then()

    private fun onNewBlocks(newBlocks: List<Block<Word>>): Mono<Void> =
        newBlocks.toFlux().flatMap { block ->
            logListenService.onBlock(NewBlockEvent(block.number().toLong(), block.hash(), block.timestamp().toLong(), null))
        }.then()

    private fun processLog(collection: String, log: LogEvent) =
        ethereum.ethGetTransactionByHash(log.transactionHash)
            .flatMap { txOption ->
                if (txOption.isEmpty) {
                    logger.info("for log $log\nnot found transaction. dropping it")
                    markLogAsDropped(log, collection)
                        .map { updatedLog -> Pair(updatedLog, null) }
                } else {
                    val tx = txOption.get()
                    val blockHash = tx.blockHash()
                    if (blockHash == null) {
                        logger.info("for log $log\nfound transaction $tx\nit's pending. skip it")
                        Mono.empty()
                    } else {
                        logger.info("for log $log\nfound transaction $tx\nit's confirmed. update logs for its block")
                        ethereum.ethGetBlockByHash(blockHash)
                            .map { block -> Pair(null, block) }
                    }
                }
            }

    private fun markLogAsDropped(log: LogEvent, collection: String): Mono<LogEvent> =
        logEventRepository.findLogEvent(collection, log.id)
            .map { it.copy(status = LogEventStatus.DROPPED, visible = false, updatedAt = Instant.now()) }
            .flatMap { logEventRepository.save(collection, it) }
            .retryOptimisticLock()

    companion object {
        val SAFE_PENDING_BLOCK_PERIOD: Duration = Duration.ofMinutes(2)
        val logger: Logger = LoggerFactory.getLogger(PendingLogsCheckJob::class.java)
    }
}
