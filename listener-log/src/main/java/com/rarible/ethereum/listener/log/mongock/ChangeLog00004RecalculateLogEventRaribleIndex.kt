package com.rarible.ethereum.listener.log.mongock

import com.github.cloudyrock.mongock.ChangeLog
import com.github.cloudyrock.mongock.ChangeSet
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.ethereum.listener.log.LogEventDescriptorHolder
import com.rarible.ethereum.listener.log.LogEventMigrationProperties
import com.rarible.ethereum.listener.log.domain.LogEvent
import io.changock.migration.api.annotations.NonLockGuarded
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.stream

@ChangeLog(order = "00004")
class ChangeLog00004RecalculateLogEventRaribleIndex {

    private val logger = LoggerFactory.getLogger(ChangeLog00004RecalculateLogEventRaribleIndex::class.java)

    private var seenLogs = 0
    private var recalculated = 0

    @ChangeSet(id = "recalculateLogEventRaribleIndex", order = "00001", runAlways = true, author = "Patrikeev")
    fun recalculateLogEventRaribleIndex(
        template: MongockTemplate,
        @NonLockGuarded logEventMigrationProperties: LogEventMigrationProperties,
        @NonLockGuarded holder: LogEventDescriptorHolder
    ) {
        if (!logEventMigrationProperties.recalculateLogEventRaribleIndex) {
            logger.info("Skip recalculation of the 'LogEvent.index' field")
            return
        }
        holder.list.map { it.collection }.distinct().forEach {
            logger.info("Recalculating 'index' for $it")
            recalculateLogEventRaribleIndex(template, it)
        }
    }

    @ChangeSet(id = "copyFixedIndexToIndexField", order = "00002", runAlways = true, author = "Patrikeev")
    fun copyFixedIndexToIndexField(
        template: MongockTemplate,
        @NonLockGuarded logEventMigrationProperties: LogEventMigrationProperties,
        @NonLockGuarded holder: LogEventDescriptorHolder
    ) {
        if (!logEventMigrationProperties.copyFixedIndexToIndexField) {
            logger.info("Skip copying 'fixedIndex' to 'index'")
            return
        }
        holder.list.map { it.collection }.distinct().forEach {
            logger.info("Copying 'fixedIndex' to 'index' field for $it")
            copyFixedIndexToIndexField(template, it)
        }
    }

    fun recalculateLogEventRaribleIndex(template: MongockTemplate, collectionName: String) {
        val query = Query(LogEvent::visible isEqualTo true)
            .with(
                Sort.by(
                    Sort.Order.asc(LogEvent::transactionHash.name),
                    Sort.Order.asc(LogEvent::topic.name),
                    Sort.Order.asc(LogEvent::address.name)
                )
            )

        // LogEvent-s having the same <transactionHash, topic, address>
        val window = arrayListOf<LogEvent>()

        template.stream<LogEvent>(query, collectionName).use { iterator ->
            for (logEvent in iterator) {
                seenLogs++
                val last = window.lastOrNull()
                val theSame = last != null &&
                        last.transactionHash == logEvent.transactionHash
                        && last.topic == logEvent.topic
                        && last.address == logEvent.address
                if (!theSame) {
                    recalculateIndexInsideWindow(template, collectionName, window)
                    window.clear()
                }
                window += logEvent
            }
        }
        if (window.isNotEmpty()) {
            recalculateIndexInsideWindow(template, collectionName, window)
        }
    }

    fun copyFixedIndexToIndexField(template: MongockTemplate, collectionName: String) {
        val query = Query(LogEvent::visible isEqualTo true)
        var updated = 0
        var seen = 0
        template.stream<LogEvent>(query, collectionName).use { iterator ->
            seen++
            for (logEvent in iterator) {
                if (logEvent.fixedIndex != null && logEvent.index != logEvent.fixedIndex) {
                    try {
                        if (++updated % 5000 == 0) {
                            logger.info("Updated $updated of total seen $seen log events")
                        }
                        template
                            .update(LogEvent::class.java)
                            .inCollection(collectionName)
                            .matching(LogEvent::id isEqualTo logEvent.id)
                            .apply(Update().set(LogEvent::index.name, logEvent.fixedIndex))
                            .first()
                    } catch (e: Exception) {
                        logger.warn("Failed to update ${logEvent.id}: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun recalculateIndexInsideWindow(
        template: MongockTemplate,
        collectionName: String,
        window: MutableList<LogEvent>
    ) {
        window.filter { it.logIndex == null }.let { brokenLogs ->
            if (brokenLogs.isNotEmpty()) {
                logger.error("Some log events are visible but have logIndex == null: ${window.joinToString { it.id.toHexString() }}")
                window.removeAll(brokenLogs)
            }
        }

        // Several LogEvents-s may have the same [logIndex] but different [minorLogIndex].
        // for them [index] must be re-calculated to the same value.
        val logIndexToFixedIndex = window.map { it.logIndex!! }.distinct().sorted()
            .mapIndexed { index, logIndex -> logIndex to index }.toMap()

        for (logEvent in window) {
            val fixedIndex = logIndexToFixedIndex.getValue(logEvent.logIndex!!)
            if (logEvent.index != fixedIndex) {
                logger.info("Recalculated (#${++recalculated} of seen $seenLogs) log ${logEvent.id}: 'index' ${logEvent.index} -> $fixedIndex: ${logEvent.id}")
                try {
                    template
                        .update(LogEvent::class.java)
                        .inCollection(collectionName)
                        .matching(LogEvent::id isEqualTo logEvent.id)
                        .apply(Update().set(LogEvent::fixedIndex.name, fixedIndex))
                        .first()
                } catch (e: Exception) {
                    logger.warn("Failed to update ${logEvent.id}: ${e.message}", e)
                }
            }
        }
    }
}