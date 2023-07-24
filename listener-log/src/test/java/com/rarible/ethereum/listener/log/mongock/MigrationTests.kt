package com.rarible.ethereum.listener.log.mongock

import com.rarible.ethereum.listener.log.AbstractIntegrationTest
import com.rarible.ethereum.listener.log.IntegrationTest
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.mock.randomLogEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

@IntegrationTest
class MigrationTests : AbstractIntegrationTest() {

    @Test
    fun `set updatedAt and createdAt field if not exists`() = runBlocking<Unit> {
        val collection = "log_event_test_collection"
        repeat(50) {
            mongo.save(randomLogEvent(), collection)
        }

        // unset updatedAt, createdAt fields
        mongo.updateMulti(
            Query(Criteria.where(LogEvent::updatedAt.name).exists(true)),
            Update().unset(LogEvent::updatedAt.name).unset(LogEvent::createdAt.name),
            collection
        )

        mongo.findAll(LogEvent::class.java, collection).asFlow().toList().forEach {
            assertThat(it.updatedAt).isEqualTo(Instant.EPOCH)
        }

        val queryMulti = ChangeLog00001.fillUpdatedAtLogIndexQuery()
        val multiUpdate = ChangeLog00001.fillUpdatedAtLogIndexUpdate()
        mongo.updateMulti(
            queryMulti,
            multiUpdate,
            collection
        )

        mongo.findAll(LogEvent::class.java, collection).asFlow().toList().forEach {
            assertThat(it.updatedAt).isNotEqualTo(Instant.EPOCH)
            assertThat(it.updatedAt).isEqualTo(it.createdAt)
        }
    }
}
