package com.rarible.ethereum.listener.log.persist

import com.rarible.core.logging.LoggingUtils
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.mongock.ChangeLog00001
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.types.ObjectId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.ne
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import scalether.domain.Address

@Component
class LogEventRepository(
    private val mongo: ReactiveMongoOperations
) {
    fun delete(collection: String, event: LogEvent): Mono<LogEvent> {
        return mongo.remove(event, collection).thenReturn(event)
    }

    fun findVisibleByKey(
        collection: String,
        transactionHash: Word,
        topic: Word,
        address: Address,
        index: Int,
        minorLogIndex: Int
    ): Mono<LogEvent> {
        val c = Criteria.where("transactionHash").`is`(transactionHash)
            .and("topic").`is`(topic)
            .and("address").`is`(address)
            .and("index").`is`(index)
            .and("minorLogIndex").`is`(minorLogIndex)
            .and("visible").`is`(true)
        return mongo.findOne(Query.query(c).withHint(ChangeLog00001.VISIBLE_INDEX_NAME), LogEvent::class.java, collection)
    }

    fun save(collection: String, event: LogEvent): Mono<LogEvent> {
        return mongo.save(event.withDbUpdated(), collection)
    }

    fun findLogEvent(collection: String, id: ObjectId): Mono<LogEvent> {
        return mongo.findById(id, LogEvent::class.java, collection)
    }

    fun countLogsByBlockNumber(collection: String, blockNumber: Long): Mono<Long> {
        val criteria = Criteria.where(LogEvent::blockNumber.name).isEqualTo(blockNumber)
        return mongo.count(Query(criteria), collection)
    }

    suspend fun hasRevertedLogEvent(collection: String, blockNumber: Long): Boolean {
        val criteria = Criteria
            .where(LogEvent::blockNumber.name).isEqualTo(blockNumber)
            .and(LogEvent::status.name).isEqualTo(LogEventStatus.REVERTED)

        return mongo.findOne(Query.query(criteria), LogEvent::class.java, collection).awaitFirstOrNull() != null
    }

    fun findAndRevert(collection: String, blockNumber: Long, blockHash: Word, topic: Word): Flux<LogEvent> {
        val query = Query().apply {
            addCriteria(LogEvent::blockNumber isEqualTo blockNumber)
            addCriteria(LogEvent::topic isEqualTo topic)
            addCriteria(LogEvent::blockHash ne blockHash)
        }
        query.with(Sort.by(Sort.Direction.DESC, LogEvent::logIndex.name, LogEvent::minorLogIndex.name))
        return LoggingUtils.withMarkerFlux { marker ->
            mongo.find(query, LogEvent::class.java, collection)
                .map {
                    logger.info(marker, "reverting $it")
                    it.copy(status = LogEventStatus.REVERTED, visible = false)
                }
                .flatMap { save(collection, it) }
        }
    }

    fun findAndDelete(collection: String, blockHash: Word, topic: Word, status: LogEventStatus? = null): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            val blockHashCriteria = Criteria.where(LogEvent::blockHash.name).isEqualTo(blockHash)
            val topicCriteria = Criteria.where(LogEvent::topic.name).isEqualTo(topic)
            val statusCriteria = status?.let { Criteria.where(LogEvent::status.name).isEqualTo(it) }

            val query = Query().apply {
                addCriteria(blockHashCriteria)
                addCriteria(topicCriteria)
                statusCriteria?.let { addCriteria(it) }
            }
            mongo
                .find(query, LogEvent::class.java, collection)
                .flatMap {
                    logger.info(marker, "Delete log event: blockHash={}, status={}", it.blockHash, it.status)
                    delete(collection, it).thenReturn(it)
                }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LogEventRepository::class.java)
    }
}
