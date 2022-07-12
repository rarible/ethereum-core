package com.rarible.ethereum.listener.log

import com.rarible.core.apm.withSpan
import com.rarible.core.apm.withTransaction
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

    fun onBlockEvent(event: NewBlockEvent, fullBlock: Block<Transaction>): Flux<LogEvent> {
        val deleteReverted = logEventRepository.findAndDelete(
            collection = collection,
            blockHash = event.hash,
            topic = topic,
            status = LogEventStatus.REVERTED
        )

        val revert = logEventRepository.findAndRevert(
            collection = collection,
            blockNumber = event.number,
            blockHash = event.hash,
            topic = topic
        ).flatMap { onRevertedLogEvent(it) }

        return Flux.concat(
            deleteReverted,
            revert,
            onNewBlock(fullBlock)
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
                                .withTransaction("reindexBlockRange", labels = listOf("range" to it.toString()))
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
        return ethereum.ethGetLogsJava(finalFilter).withSpan("getLogs")
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
                .withSpan("getBlock", labels = listOf("blockHash" to logs.blockHash))
                .flatMapMany { block ->
                    processLogs(marker, block, logs.logs).withSpan(
                        "processLogs", labels = listOf("blockHash" to logs.blockHash)
                    )
                }
        }.loggerContext(mapOf("blockHash" to "${logs.blockHash}"))
            .withSpan("reindex", labels = listOf("blockHash" to logs.blockHash))
    }

    private fun processLogs(marker: Marker, block: Block<Transaction>, logs: List<Log>): Flux<LogEvent> {
        val timestamp = block.timestamp().toLong()
        val transactions = Lists.toJava(block.transactions()).associateBy { transaction -> transaction.hash() }

        val indexedLogs = logs
            .groupBy { it.transactionHash() to it.address() }.values
            .flatMap { logsGroupedByTransactionAndAddress ->
                logsGroupedByTransactionAndAddress
                    .sortedBy { log -> log.logIndex() }
                    .mapIndexed { index, log -> Indexed(index, logsGroupedByTransactionAndAddress.size, log) }
            }.toFlux()

        return indexedLogs
            .flatMap { (index, total, log) ->
                val transaction = transactions[log.transactionHash()] ?: error("Can't find transaction for log $log")
                onLog(marker, index, total, log, transaction, timestamp)
            }
            .withSpan("onLogs")
    }

    private fun findTheSameLogEvent(toSave: LogEvent): Mono<LogEvent> =
        logEventRepository.findVisibleByKey(
            collection = descriptor.collection,
            transactionHash = toSave.transactionHash,
            topic = toSave.topic,
            address = toSave.address,
            index = toSave.index,
            minorLogIndex = toSave.minorLogIndex
        )

    private fun onLog(
        marker: Marker, index: Int, total: Int, log: Log, transaction: Transaction, timestamp: Long
    ): Flux<LogEvent> {
        logger.debug(marker, "onLog $log")

        return descriptor.convert(log, transaction, timestamp, index, total).toFlux()
            .doOnError { logger.error(marker, "failed to convert logs from $log", it) }
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
                        from = transaction.from(),
                        to = transaction.to(),
                        logIndex = log.logIndex().toInt(),
                        minorLogIndex = minorLogIndex,
                        index = index,
                        visible = true,
                        blockTimestamp = Instant.now().epochSecond,
                        createdAt = Instant.now()
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
                                    val withCorrectId = toSave.copy(
                                        id = found.id, version = found.version, updatedAt = Instant.now()
                                    )

                                    if (equals(withCorrectId, found).not()) {
                                        logger.debug(
                                            marker, "Saving changed LogEvent (${descriptor.collection}): $withCorrectId"
                                        )
                                        logEventRepository.save(descriptor.collection, withCorrectId)
                                    } else {
                                        logger.debug(
                                            marker, "LogEvent didn't change (${descriptor.collection}): $withCorrectId"
                                        )
                                        found.justOrEmpty()
                                    }
                                } else {
                                    logger.debug(marker, "Saving new LogEvent (${descriptor.collection}): $toSave")
                                    logEventRepository.save(descriptor.collection, toSave)
                                }
                            }
                    }
                    .flatMap { savedEvent -> onLogEvent(savedEvent) }
                    .retryOptimisticLock(3)
            }
    }

    private fun onLogEvent(event: LogEvent): Mono<LogEvent> {
        return Flux.concat(onLogEventListeners.map { listener -> listener.onLogEvent(event) })
            .then(Mono.just(event))
    }

    private fun onRevertedLogEvent(event: LogEvent): Mono<LogEvent> {
        return Flux.concat(onLogEventListeners.map { listener -> listener.onRevertedLogEvent(event) })
            .then(Mono.just(event))
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

data class Indexed<out T>(val index: Int, val total: Int, val value: T)