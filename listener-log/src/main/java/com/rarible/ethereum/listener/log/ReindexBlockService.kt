package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.abs

@Service
class ReindexBlockService(
    private val blockRepository: BlockRepository,
    private val logListenService: LogListenService,
    @Value("\${ethereumReindexBlockBatchSize:1}") private val batchSize: Int,
) {

    fun indexPendingBlocks(): Mono<Void> {
        return Flux.concat(
            blockRepository.findByStatus(BlockStatus.PENDING)
                .filter { abs(System.currentTimeMillis() / 1000 - it.timestamp) > 60 },
            blockRepository.findByStatus(BlockStatus.ERROR)
        ).buffer(batchSize)
            .concatMap { logListenService.reindexBlocks(it) }
            .then()
    }
}