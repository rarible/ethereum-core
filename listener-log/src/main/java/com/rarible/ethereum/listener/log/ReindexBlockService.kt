package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.time.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ReindexBlockService(
    private val blockRepository: BlockRepository,
    private val logListenService: LogListenService,
    @Value("\${ethereumReindexBatchSize:5}") private val ethereumReindexBatchSize: Int,
    @Value("\${ethereumReindexBlockTimeout:5000}") private val ethereumReindexBlockTimeout: Long,
) {
    fun indexPendingBlocks() = mono {
        val pending = getBlocks(BlockStatus.PENDING)
        val errors = getBlocks(BlockStatus.ERROR)
        coroutineScope {
            val pendingDeferred = async { reindexBlocks(pending) }
            val errorDeferred = async { reindexBlocks(errors) }

            pendingDeferred.await()
            errorDeferred.await()
        }
    }

    private suspend fun reindexBlocks(blocks: List<BlockHead>) = coroutineScope {
        blocks
            .chunked(ethereumReindexBatchSize)
            .map { chunk ->
                chunk.map {
                    async { reindexBlock(it) }
                }.awaitAll()
            }
            .lastOrNull()
    }

    private suspend fun reindexBlock(block: BlockHead) {
        try {
            withTimeout(Duration.ofMillis(ethereumReindexBlockTimeout)) {
                logListenService.reindexBlock(block).awaitFirstOrNull()
            }
        } catch (ex: Throwable) {
            logger.error("Can't reindex block ${block.id}", ex)
        }
    }

    private suspend fun getBlocks(status: BlockStatus): List<BlockHead> {
        return blockRepository.findByStatus(status).collectList().awaitSingle()
    }

    private val logger = LoggerFactory.getLogger(ReindexBlockService::class.java)
}
