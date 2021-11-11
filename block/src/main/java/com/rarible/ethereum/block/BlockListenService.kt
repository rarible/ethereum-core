package com.rarible.ethereum.block

import com.rarible.core.common.toOptional
import com.rarible.core.logging.LoggingUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface Block {
    val blockNumber: Long
    val blockHash: io.daonomic.rpc.domain.Bytes
    val parentBlockHash: io.daonomic.rpc.domain.Bytes
}

class BlockListenService<B : Block>(
    private val state: BlockState<B>,
    private val blockchain: Blockchain<B>
) {
    fun listen(): Flux<BlockEvent<B>> {
        return LoggingUtils.withMarkerFlux { marker ->
            blockchain.listenNewBlocks()
                .concatMap { getNewBlocks(marker, it).concatMap { newBlock -> insertOrUpdateBlock(marker, newBlock) } }
        }
    }

    private fun getNewBlocks(marker: Marker, newBlock: B): Flux<B> {
        return state.getLastKnownBlock().toOptional()
            .flatMapMany { lastKnown ->
                when {
                    !lastKnown.isPresent -> Flux.just(newBlock)
                    else -> {
                        val range = (lastKnown.get() + 1) until newBlock.blockNumber
                        if (range.last >= range.first) {
                            logger.info(marker, "getting missing blocks: $range")
                        }
                        Flux.concat(
                            Flux.fromIterable(range.asIterable())
                                .concatMap { blockchain.getBlock(it) },
                            Flux.just(newBlock)
                        )
                    }
                }
            }
    }

    /**
     * when inserting/updating block we need to inspect parent blocks if chain was reorganized
     */
    private fun insertOrUpdateBlock(marker: Marker, b: B): Flux<BlockEvent<B>> {
        logger.info(marker, "insertOrUpdateBlock $b")
        return checkAndEmitBlockEvents(marker, b)
            .collectList()
            .flatMapMany { Flux.fromIterable(it.asReversed()) }
            .concatMap {
                state.saveKnownBlock(it.block).thenReturn(it)
            }
    }

    private fun checkAndEmitBlockEvents(marker: Marker, b: B): Flux<BlockEvent<B>> =
        checkNewBlock(marker, b, false).expandDeep {
            if (it.block.blockNumber == 0L) {
                Mono.empty<BlockEvent<B>>()
            } else {
                blockchain.getBlock(it.block.blockNumber - 1)
                    .flatMap { prev -> checkNewBlock(marker, prev, true) }
            }
        }

    private fun checkNewBlock(marker: Marker, b: B, emptyOnUnknownHash: Boolean): Mono<BlockEvent<B>> {
        return state.getBlockHash(b.blockNumber).toOptional()
            .flatMap { knownHash ->
                when {
                    !knownHash.isPresent && emptyOnUnknownHash -> Mono.empty()
                    !knownHash.isPresent -> {
                        logger.info(marker, "block ${b.blockNumber} ${b.blockHash} not found. is new block")
                        Mono.just(BlockEvent(b))
                    }
                    knownHash.isPresent && knownHash.get() != b.blockHash -> {
                        logger.info(marker, "block ${b.blockNumber} ${b.blockHash} found. hash differs")
                        Mono.just(BlockEvent(b, BlockInfo(knownHash.get(), b.blockNumber)))
                    }
                    else -> {
                        logger.info(marker, "block ${b.blockNumber} ${b.blockHash} found. hash is the same")
                        Mono.empty<BlockEvent<B>>()
                    }
                }
            }
            .doOnSubscribe {
                logger.info(marker, "checkNewBlock ${b.blockNumber} ${b.blockHash}")
            }
            .doOnError {
                logger.error(marker, "checkNewBlock failed ${b.blockNumber} ${b.blockHash}", it)
            }
            .doOnTerminate {
                logger.info(marker, "checkNewBlock completed ${b.blockNumber} ${b.blockHash}")
            }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(BlockListenService::class.java)
    }
}