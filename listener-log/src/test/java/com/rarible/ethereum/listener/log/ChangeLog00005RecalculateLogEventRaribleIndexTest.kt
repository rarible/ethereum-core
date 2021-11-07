package com.rarible.ethereum.listener.log

import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.LockGuardInvoker
import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.VoidSupplier
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.core.test.data.randomAddress
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.mock.randomLogEvent
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.mongock.ChangeLog00001
import com.rarible.ethereum.listener.log.mongock.ChangeLog00005RecalculateLogEventRaribleIndex
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.update
import java.util.function.Supplier

@IntegrationTest
class ChangeLog00005RecalculateLogEventRaribleIndexTest : AbstractIntegrationTest() {
    private val migration = ChangeLog00005RecalculateLogEventRaribleIndex()

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
        // Make sure the database contains the new rarible index, otherwise "bad hint" error is thrown
        ChangeLog00001().createLogEventIndexContainingAddress(mongockTemplate, collectionName)
        Thread.sleep(1000)

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

        run {
            val brokenEvent = randomLogEvent()
            saveLogs(brokenEvent)
            mongockTemplate.update<LogEvent>()
                .inCollection(collectionName)
                .matching(LogEvent::id isEqualTo brokenEvent.id)
                .apply(Update().set("data._class", "unknown class"))
                .first()
        }

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
        // Make sure the database contains the new rarible index, otherwise "bad hint" error is thrown
        ChangeLog00001().createLogEventIndexContainingAddress(mongockTemplate, collectionName)
        Thread.sleep(1000)

        val event1 = randomLogEvent().copy(fixedIndex = 2)
        val notChangedEvents = listOf(randomLogEvent())
        saveLogs(event1)
        saveLogs(*notChangedEvents.toTypedArray())
        run {
            val brokenEvent = randomLogEvent()
            saveLogs(brokenEvent)
            mongockTemplate.update<LogEvent>()
                .inCollection(collectionName)
                .matching(LogEvent::id isEqualTo brokenEvent.id)
                .apply(Update().set("data._class", "unknown class"))
                .first()
        }
        migration.copyFixedIndexToIndexField(mongockTemplate, collectionName)
        val fixedLog = find(event1)
        assertThat(fixedLog.index).isEqualTo(2)
        assertThat(fixedLog.oldIndex).isEqualTo(event1.index)
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
}