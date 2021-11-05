package com.rarible.ethereum.listener.log.mongock

import com.github.cloudyrock.mongock.ChangeLog
import com.github.cloudyrock.mongock.ChangeSet
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.ethereum.listener.log.LogEventDescriptorHolder
import com.rarible.ethereum.listener.log.LogEventMigrationProperties
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import io.changock.migration.api.annotations.NonLockGuarded
import io.daonomic.rpc.domain.Word
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.stream

@ChangeLog(order = "00005")
class ChangeLog00005MarkLogsRevertedForRevertedBlocks {

    private val logger = LoggerFactory.getLogger(ChangeLog00005MarkLogsRevertedForRevertedBlocks::class.java)

    @ChangeSet(id = "markLogsRevertedForRevertedBlocks", order = "00001", runAlways = true, author = "Patrikeev")
    fun recalculateLogEventRaribleIndex(
        template: MongockTemplate,
        @NonLockGuarded logEventMigrationProperties: LogEventMigrationProperties,
        @NonLockGuarded holder: LogEventDescriptorHolder
    ) {
        if (!logEventMigrationProperties.markLogsRevertedForRevertedBlocks) {
            logger.info("Skip: 'markLogsRevertedForRevertedBlocks'")
            return
        }
        holder.list.map { it.collection }.distinct().forEach {
            logger.info("Marking logs REVERTED for reverted blocks in $it")
            markLogsRevertedForRevertedBlocks(template, it)
        }
    }

    fun markLogsRevertedForRevertedBlocks(template: MongockTemplate, collectionName: String) {
        val query = Query(LogEvent::visible isEqualTo true)
        var failed = 0
        var seen = 0
        template.stream<LogEvent>(query, collectionName).use { iterator ->
            for (logEvent in iterator) {
                seen++
                if (logEvent.blockNumber == null) {
                    logger.error("Unknown blockNumber for ${logEvent.id}")
                    continue
                }
                val correctHash = getCorrectBlockHash(template, logEvent.blockNumber!!)
                if (correctHash == null) {
                    logger.error("Unknown block hash for ${logEvent.blockNumber}")
                    continue
                }
                if (correctHash != logEvent.blockHash) {
                    try {
                        logger.info("Reverting ${logEvent.id}: block #${logEvent.blockNumber} hash = $correctHash, but actual = ${logEvent.blockHash} (seen = $seen, failed = $failed)")
                        template
                            .update(LogEvent::class.java)
                            .inCollection(collectionName)
                            .matching(LogEvent::id isEqualTo logEvent.id)
                            .apply(Update().set("mustBeReverted", true))
                            .first()
                    } catch (e: Exception) {
                        failed++
                        logger.warn("Failed to update ${logEvent.id}: ${e.message}", e)
                    }
                }
            }
        }
    }

    private val correctBlockHashes = object : LinkedHashMap<Long, Word>(10, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Word>?): Boolean = size > 1000
    }

    private fun getCorrectBlockHash(mongockTemplate: MongockTemplate, blockNumber: Long): Word? {
        correctBlockHashes[blockNumber]?.let { return it }
        val correctHash = mongockTemplate.findById(blockNumber, BlockHead::class.java)?.hash ?: return null
        correctBlockHashes[blockNumber] = correctHash
        return correctHash
    }
}