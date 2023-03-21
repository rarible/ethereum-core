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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ReindexBlockService(
    private val blockRepository: BlockRepository,
    private val logListenService: LogListenService,
    @Value("\${ethereumReindexBatchSize:5}") private val ethereumReindexBatchSize: Int
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
        logListenService.reindexBlock(block).awaitFirstOrNull()
    }

    private suspend fun getBlocks(status: BlockStatus): List<BlockHead> {
        return blockRepository.findByStatus(status).collectList().awaitSingle()
    }
}