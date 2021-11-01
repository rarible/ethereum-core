package com.rarible.ethereum.listener.log

import com.rarible.core.apm.withSpan
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
import io.daonomic.rpc.domain.Word
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
import scalether.domain.response.Transaction
import scalether.java.Lists
import scalether.util.Hex
import java.math.BigInteger
import java.time.Instant

class LogEventListener<T : EventData>(
    val descriptor: LogEventDescriptor<T>,
    private val onLogEventListeners: List<OnLogEventListener>,
    private val pendingLogService: PendingLogService,
    private val logEventMigrationProperties: LogEventMigrationProperties,
    private val logEventRepository: LogEventRepository,
    private val ethereum: MonoEthereum,
    private val backoff: RetryBackoffSpec,
    private val batchSize: Long
) {

    private val logger: Logger = LoggerFactory.getLogger(descriptor.javaClass)
    private val collection = descriptor.collection
    private val topic = descriptor.topic

    init {
        logger.info(
            "Creating LogEventListener for ${descriptor.javaClass.simpleName}, got onLogEventListeners: ${onLogEventListeners.joinToString { it.javaClass.simpleName }}"
        )
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
            ethereum.ethGetFullBlockByHash(event.hash).withSpan("getFullBlock")
                .doOnError { th -> logger.warn("Unable to get block by hash: " + event.hash, th) }
                .retryWhen(backoff)
                .flatMapMany { block ->
                    Flux.concat(
                        pendingLogService.markInactive(descriptor.collection, descriptor.topic, block)
                            .withSpan("pending"),
                        onNewBlock(block)
                    )
                }
        ).withSpan("processTopicLogs", labels = listOf("topic" to topic.toString()))
    }

    private fun onNewBlock(block: Block<Transaction>): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            descriptor.getAddresses()
                .flatMap { contracts ->
                    val filter = LogFilter
                        .apply(TopicFilter.simple(descriptor.topic))
                        .address(*contracts.toTypedArray())
                        .blockHash(block.hash())
                    ethereum.ethGetLogsJava(filter).withSpan("getLogs")
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
            ethereum
                .ethGetFullBlockByHash(logs.blockHash)
                .flatMapMany { block -> processLogs(marker, block, logs.logs) }
        }.loggerContext(mapOf("blockHash" to "${logs.blockHash}"))
    }

    private fun processLogs(marker: Marker, block: Block<Transaction>, logs: List<Log>): Flux<LogEvent> {
        val timestamp = block.timestamp().toLong()
        val transactions = Lists.toJava(block.transactions()).associateBy { transaction -> transaction.hash() }

        val indexedLogs = if (logEventMigrationProperties.useNewIndex) {
            logs
                .groupBy { it.transactionHash() to it.address() }.values
                .flatMap { logsGroupedByTransactionAndAddress ->
                    logsGroupedByTransactionAndAddress.sortedBy { log -> log.logIndex() }.withIndex()
                }.toFlux()
        } else {
            logs
                .groupBy { it.transactionHash() }.values.toFlux()
                .flatMap { logsInTransaction ->
                    logsInTransaction.sortedBy { log -> log.logIndex() }.withIndex().toFlux()
                }
        }

        return indexedLogs
            .flatMap { (index, log) ->
                val transaction = transactions[log.transactionHash()] ?: error("Can't find transaction for log $log")
                onLog(marker, index, log, transaction, timestamp)
            }
            .withSpan("onLogs")
    }

    private fun findTheSameLogEvent(toSave: LogEvent): Mono<LogEvent> {
        return if (logEventMigrationProperties.useNewIndex) {
            logEventRepository.findVisibleByNewKey(
                collection = descriptor.collection,
                transactionHash = toSave.transactionHash,
                topic = toSave.topic,
                address = toSave.address,
                index = toSave.index,
                minorLogIndex = toSave.minorLogIndex
            )
        } else {
            return logEventRepository.findVisibleByKey(descriptor.collection, toSave.transactionHash, toSave.topic, toSave.index, toSave.minorLogIndex)
                .switchIfEmpty(logEventRepository.findByKey(descriptor.collection, toSave.transactionHash, toSave.blockHash!!, toSave.logIndex!!, toSave.minorLogIndex))
        }
    }

    private fun onLog(marker: Marker, index: Int, log: Log, transaction: Transaction, timestamp: Long): Flux<LogEvent> {
        logger.debug(marker, "onLog $log")

        return descriptor.convert(log, transaction, timestamp).toFlux()
            .collectList()
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
                        visible = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                }
            }
            .flatMap {
                Mono.just(it)
                    .flatMap { toSave ->
                        findTheSameLogEvent(toSave)
                            .toOptional()
                            .flatMap { opt ->
                                if (opt.isPresent) {
                                    val found = opt.get()
                                    val withCorrectId = toSave.copy(id = found.id, version = found.version, updatedAt = Instant.now())

                                    if (equals(withCorrectId, found).not()) {
                                        logger.debug(marker, "Saving changed LogEvent (${descriptor.collection}): $withCorrectId")
                                        logEventRepository.save(descriptor.collection, withCorrectId)
                                    } else {
                                        logger.debug(marker, "LogEvent didn't change (${descriptor.collection}): $withCorrectId")
                                        found.justOrEmpty()
                                    }
                                } else {
                                    logger.debug(marker, "Saving new LogEvent (${descriptor.collection}): $toSave")
                                    logEventRepository.save(descriptor.collection, toSave)
                                }
                            }
                    }
                    .flatMap { savedEvent ->
                        Flux
                            .concat(onLogEventListeners.map { listener -> listener.onLogEvent(savedEvent) })
                            .then(Mono.just(savedEvent))
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

private fun equals(first: LogEvent, second: LogEvent): Boolean {
    val fixedFirst = first.copy(updatedAt = Instant.EPOCH, createdAt = Instant.EPOCH)
    val fixedSecond = second.copy(updatedAt = Instant.EPOCH, createdAt = Instant.EPOCH)
    return fixedFirst == fixedSecond
}

data class BlockLogs(val blockHash: Word, val logs: List<Log>)
