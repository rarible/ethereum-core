package com.rarible.ethereum.listener.log

import com.rarible.core.common.justOrEmpty
import com.rarible.core.common.retryOptimisticLock
import com.rarible.core.common.toOptional
import com.rarible.core.logging.LoggingUtils
import com.rarible.core.logging.loggerContext
import com.rarible.ethereum.listener.log.domain.EventData
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.domain.NewBlockEvent
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.rpc.domain.Word
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.util.retry.RetryBackoffSpec
import scalether.core.MonoEthereum
import scalether.domain.request.LogFilter
import scalether.domain.request.TopicFilter
import scalether.domain.response.Block
import scalether.domain.response.Log
import scalether.util.Hex
import java.math.BigInteger

class LogEventListener<T : EventData>(
    val descriptor: LogEventDescriptor<T>,
    private val pendingLogService: PendingLogService,
    private val logEventRepository: LogEventRepository,
    private val ethereum: MonoEthereum,
    private val backoff: RetryBackoffSpec,
    private val batchSize: Long
) {

    private val logger: Logger = LoggerFactory.getLogger(descriptor.javaClass)
    private val collection = descriptor.collection
    private val topic = descriptor.topic

    init {
        logger.info("Creating LogEventListener for ${descriptor.javaClass.simpleName}")
    }

    fun onBlockEvent(event: NewBlockEvent): Flux<LogEvent> {
        val start: Flux<LogEvent> = if (event.reverted != null) {
            logEventRepository
                .findAndDelete(collection, event.hash, topic, LogEventStatus.REVERTED)
                .thenMany(logEventRepository.findAndRevert(collection, topic, event.reverted!!))
        } else {
            logEventRepository.findAndDelete(collection, event.hash, topic, LogEventStatus.REVERTED)
                .thenMany(Flux.empty())
        }
        return Flux.concat(
            start,
            ethereum.ethGetFullBlockByHash(event.hash)
                .doOnError { th -> logger.warn("Unable to get block by hash: " + event.hash, th) }
                .retryWhen(backoff)
                .flatMapMany { block ->
                    Flux.concat(
                        pendingLogService.markInactive(descriptor.collection, descriptor.topic, block),
                        onNewBlock(block)
                    )
                }
        )
    }

    private fun onNewBlock(block: Block<*>): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            descriptor.getAddresses()
                .flatMap { contracts ->
                    val filter = LogFilter
                        .apply(TopicFilter.simple(descriptor.topic))
                        .address(*contracts.toTypedArray())
                        .blockHash(block.hash())
                    ethereum.ethGetLogsJava(filter)
                        .doOnError { logger.warn(marker, "Unable to get logs for block ${block.hash()}", it) }
                        .retryWhen(backoff)
                }
                .flatMapMany { processLogs(marker, block, it) }
        }
    }

    fun reindex(from: Long, to: Long): Flux<LongRange> {
        return LoggingUtils.withMarkerFlux { marker ->
            logger.info(marker, "loading logs in batches from=$from to=$to batchSize=$batchSize")
            descriptor.getAddresses()
                .map { LogFilter.apply(TopicFilter.simple(descriptor.topic)).address(*it.toTypedArray()) }
                .flatMapMany { filter ->
                    BlockRanges.getRanges(from, to, batchSize)
                        .concatMap {
                            reindexBlockRange(marker, filter, it).thenReturn(it)
                        }
                }
        }
    }

    private fun reindexBlockRange(marker: Marker, filter: LogFilter, range: LongRange): Mono<Void> {
        val finalFilter = filter.blocks(
            BigInteger.valueOf(range.first).encodeForFilter(),
            BigInteger.valueOf(range.last).encodeForFilter()
        )
        logger.info(marker, "loading logs $finalFilter range=$range")
        return ethereum.ethGetLogsJava(finalFilter)
            .doOnNext {
                logger.info(marker, "loaded ${it.size} logs for range $range")
            }
            .flatMapMany { it.groupByBlock() }
            .flatMap { reindexBlock(it) }
            .then()
    }

    private fun reindexBlock(logs: BlockLogs): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            logger.info(marker, "reindex. processing block ${logs.blockHash} logs: ${logs.logs.size}")
            ethereum.ethGetBlockByHash(logs.blockHash)
                .flatMapMany { block -> processLogs(marker, block, logs.logs) }
        }.loggerContext(mapOf("blockHash" to "${logs.blockHash}"))
    }

    private fun processLogs(marker: Marker, block: Block<*>, logs: List<Log>): Flux<LogEvent> {
        if (logs.isNotEmpty()) {
            logger.info(marker, "processLogs ${logs.size} logs")
        }
        val timestamp = block.timestamp().toLong()
        return logs.groupBy { it.transactionHash() }.values.toFlux()
            .flatMap { logsInTransaction -> logsInTransaction.sortedBy { log -> log.logIndex() }.withIndex().toFlux() }
            .flatMap { (idx, log) -> onLog(marker, idx, log, timestamp) }
    }

    private fun onLog(marker: Marker, index: Int, log: Log, timestamp: Long): Flux<LogEvent> {
        logger.info(marker, "onLog $log")

        return descriptor.convert(log, timestamp).toFlux()
            .collectList()
            .onErrorResume {
                logger.error(marker, "Unable to convert log $log", it)
                Mono.empty()
            }
            .flatMapIterable { dataCollection ->
                dataCollection.mapIndexed { minorLogIndex, data ->
                    LogEvent(
                        data = data,
                        address = log.address(),
                        topic = descriptor.topic,
                        transactionHash = log.transactionHash(),
                        status = LogEventStatus.CONFIRMED,
                        blockHash = log.blockHash(),
                        blockNumber = log.blockNumber().toLong(),
                        logIndex = log.logIndex().toInt(),
                        minorLogIndex = minorLogIndex,
                        index = index,
                        visible = true
                    )
                }
            }
            .flatMap {
                Mono.just(it)
                    .flatMap { toSave ->
                        logger.info(marker, "saving $toSave to ${descriptor.collection}")
                        logEventRepository.findVisibleByKey(descriptor.collection, toSave.transactionHash, toSave.topic, toSave.index, toSave.minorLogIndex)
                            .toOptional()
                            .flatMap { opt ->
                                if (opt.isPresent) {
                                    val found = opt.get()
                                    val withCorrectId = toSave.copy(id = found.id, version = found.version)
                                    if (withCorrectId != found) {
                                        logger.info(marker, "Saving changed LogEvent $withCorrectId to ${descriptor.collection}")
                                        logEventRepository.save(descriptor.collection, withCorrectId)
                                    } else {
                                        logger.info(marker, "LogEvent didn't change: $withCorrectId")
                                        found.justOrEmpty()
                                    }
                                } else {
                                    logger.info(marker, "Saving new LogEvent $toSave")
                                    logEventRepository.save(descriptor.collection, toSave)
                                }
                            }
                    }
                    .retryOptimisticLock(3)
            }
    }
}

private fun BigInteger.encodeForFilter(): String {
    return "0x${Hex.to(this.toByteArray()).trimStart('0')}"
}

private fun List<Log>.groupByBlock(): Flux<BlockLogs> {
    return Flux.fromIterable(this.groupBy { it.blockHash() }.entries.map { e -> BlockLogs(e.key, e.value) })
}

data class BlockLogs(val blockHash: Word, val logs: List<Log>)
