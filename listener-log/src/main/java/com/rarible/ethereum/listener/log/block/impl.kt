package com.rarible.ethereum.listener.log.block

import com.rarible.ethereum.block.BlockState
import com.rarible.ethereum.block.Blockchain
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.daonomic.rpc.domain.Bytes
import io.daonomic.rpc.domain.Word
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import scalether.core.EthPubSub
import scalether.core.MonoEthereum
import scalether.domain.response.Block
import java.math.BigInteger

@Component
class EthereumBlockchain(
    private val ethereum: MonoEthereum,
    private val ethPubSub: EthPubSub
) : Blockchain<SimpleBlock> {

    override fun getBlock(hash: Bytes): Mono<SimpleBlock> {
        return ethereum.ethGetBlockByHash(Word.apply(hash))
            .map { SimpleBlock(it) }
    }

    override fun getBlock(number: Long): Mono<SimpleBlock> {
        return ethereum.ethGetBlockByNumber(BigInteger.valueOf(number))
            .map { SimpleBlock(it) }
    }

    override fun getLastKnownBlock(): Mono<Long> {
        return ethereum.ethBlockNumber()
            .map { it.toLong() }
    }

    override fun listenNewBlocks(): Flux<SimpleBlock> {
        return ethPubSub.newHeads()
            .map { SimpleBlock(it) }
    }
}

@Component
class BlockStateImpl(
    private val blockHeadRepository: BlockRepository,
    private val template: ReactiveMongoOperations,
    @Value("\${fakeLastKnown:false}") private val fakeLastKnown: Boolean
) : BlockState<SimpleBlock> {

    override fun getLastKnownBlock(): Mono<Long> {
        return if (fakeLastKnown) {
            Mono.empty()
        } else {
            template.findOne<BlockHead>(Query().with(Sort.by(Sort.Direction.DESC, "_id")))
                .map { it.id }
        }
    }

    override fun saveKnownBlock(block: SimpleBlock): Mono<Void> {
        logger.info("saveKnownBlock $block")
        return blockHeadRepository.saveR(BlockHead(block.number, block.hash, block.timestamp)).then()
    }

    override fun getBlockHash(number: Long): Mono<Bytes> {
        return blockHeadRepository.findByIdR(number)
            .map { it.hash }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(BlockStateImpl::class.java)
    }
}

data class SimpleBlock(
    val hash: Word,
    val parentHash: Word,
    val number: Long,
    val timestamp: Long
) : com.rarible.ethereum.block.Block {

    constructor(block: Block<*>) : this(block.hash(), block.parentHash(), block.number().toLong(), block.timestamp().toLong())

    override val blockHash: Bytes
        get() = hash
    override val parentBlockHash: Bytes
        get() = parentHash
    override val blockNumber: Long
        get() = number
}
