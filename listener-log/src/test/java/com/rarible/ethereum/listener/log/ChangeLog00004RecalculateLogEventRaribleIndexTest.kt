package com.rarible.ethereum.listener.log

import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.LockGuardInvoker
import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.VoidSupplier
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomWord
import com.rarible.ethereum.listener.log.domain.EventData
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.mongock.ChangeLog00004RecalculateLogEventRaribleIndex
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import io.daonomic.rpc.domain.Word
import org.assertj.core.api.Assertions.assertThat
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.function.Supplier

@Disabled
class ChangeLog00004RecalculateLogEventRaribleIndexTest : AbstractIntegrationTest() {
    private val migration = ChangeLog00004RecalculateLogEventRaribleIndex()

    private lateinit var mongockTemplate: MongockTemplate

    @Autowired
    private lateinit var logEventRepository: LogEventRepository

    private val collectionName = "testCollection"

    @BeforeEach
    fun initializeMongockTemplate() {
        mongockTemplate = MongockTemplate(mongoTemplate, object : LockGuardInvoker {
            override fun <T> invoke(supplier: Supplier<T>): T = supplier.get()
            override fun invoke(supplier: VoidSupplier) {
                supplier.execute()
            }
        })
    }

    @Test
    internal fun `recalculate indexes`() {
        val event1 = randomLogEvent()
        val event2 = event1.copy(
            address = randomAddress(), // Different address.
            logIndex = event1.logIndex!! + 1,
            index = event1.index + 1,
            id = ObjectId()
        )
        val event3 = event2.copy( // Same as 2 but the next by [minorLogIndex].
            minorLogIndex = 1,
            id = ObjectId()
        )
        val notChangedEvents = listOf(
            randomLogEvent(),
            event1.copy(topic = randomWordd(), id = ObjectId()) // Same transaction but a different topic.
        )
        saveLogs(event1, event2, event3)
        saveLogs(*notChangedEvents.toTypedArray())

        assertThat(find(event1).index).isEqualTo(0)
        assertThat(find(event2).index).isEqualTo(1)
        assertThat(find(event2).index).isEqualTo(1)
        for (event in notChangedEvents) {
            assertThat(find(event).index).isEqualTo(event.index)
        }

        migration.recalculateLogEventRaribleIndex(mongockTemplate, collectionName)
        // 'index' of event2 must become 0 because now it is calculated inside the group of <transactionHash, topic, address>
        // 'index' of event3 must become 0 the same as event2

        assertThat(find(event1).fixedIndex).isEqualTo(null) // Must not change.
        assertThat(find(event2).fixedIndex).isEqualTo(0)
        assertThat(find(event3).fixedIndex).isEqualTo(0)
        for (event in notChangedEvents) {
            assertThat(find(event).fixedIndex).isEqualTo(null)
        }
    }

    @Test
    internal fun `copy fixedIndex to index`() {
        val event1 = randomLogEvent().copy(fixedIndex = 2)
        val notChangedEvents = listOf(randomLogEvent())
        saveLogs(event1)
        saveLogs(*notChangedEvents.toTypedArray())
        migration.copyFixedIndexToIndexField(mongockTemplate, collectionName)
        assertThat(find(event1).index).isEqualTo(2)
        for (event in notChangedEvents) {
            assertThat(find(event).index).isEqualTo(event.index)
        }
    }

    private fun saveLogs(vararg logEvents: LogEvent) {
        logEvents.forEach {
            logEventRepository.save(collectionName, it).block()
        }
    }

    private fun find(logEvent: LogEvent): LogEvent =
        logEventRepository.findLogEvent(collectionName, logEvent.id).block()!!

    private fun randomLogEvent() =
        LogEvent(
            blockNumber = 0,
            blockHash = randomWordd(),
            transactionHash = randomWordd(),
            address = randomAddress(),
            topic = randomWordd(),

            logIndex = 0,
            index = 0,
            minorLogIndex = 0,

            status = LogEventStatus.CONFIRMED,

            data = object : EventData {},
            visible = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    private fun randomWordd() = Word.apply(randomWord())
}