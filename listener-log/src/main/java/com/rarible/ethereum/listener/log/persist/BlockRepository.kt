package com.rarible.ethereum.listener.log.persist

import com.rarible.core.logging.LoggingUtils
import com.rarible.core.mongo.repository.AbstractMongoRepository
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.rpc.domain.Word
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class BlockRepository(
    mongo: ReactiveMongoOperations
) : AbstractMongoRepository<BlockHead, Long>(mongo, BlockHead::class.java) {

    fun findByStatus(status: BlockStatus): Flux<BlockHead> =
        mongo.find(Query(BlockHead::status isEqualTo status))

    fun getLastBlock(): Mono<Long> {
        return mongo.find(Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1), BlockHead::class.java)
            .next()
            .map { it.id }
    }

    fun updateBlockStatus(number: Long, hash: Word, status: BlockStatus): Mono<Void> {
        return LoggingUtils.withMarker { marker ->
            logger.info(marker, "updateBlockStatus $number $hash $status")
            val c = Criteria.where("_id").`is`(number).and("hash").`is`(hash)
            mongo.updateFirst(Query(c), Update().set("status", status), BlockHead::class.java).then()
        }
    }

    fun findFirstByIdAsc(): Mono<BlockHead> =
        mongo.findOne(Query().with(Sort.by(Sort.Direction.ASC, BlockHead::id.name)))

    companion object {
        val logger: Logger = LoggerFactory.getLogger(BlockRepository::class.java)
    }
}