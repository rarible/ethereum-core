package com.rarible.ethereum.listener.log.mongock

import com.github.cloudyrock.mongock.ChangeLog
import com.github.cloudyrock.mongock.ChangeSet
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.ethereum.listener.log.LogEventDescriptorHolder
import io.changock.migration.api.annotations.NonLockGuarded
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

@ChangeLog(order = "00001")
class ChangeLog00001 {

    @ChangeSet(id = "fillMinorLogIndex", order = "0000", author = "eugene")
    fun fillMinorLogIndex(template: MongockTemplate, @NonLockGuarded holder: LogEventDescriptorHolder) {
        val collections = holder.list.map { it.collection }.toSet()
        collections.forEach {
            template.updateMulti(Query(Criteria.where("minorLogIndex").exists(false)), Update().set("minorLogIndex", 0), it)
        }
    }

    @ChangeSet(id = "ensureInitialIndexes", order = "00001", author = "eugene")
    fun ensureInitialIndexes(template: MongockTemplate, @NonLockGuarded holder: LogEventDescriptorHolder) {
        val collections = holder.list.map { it.collection }.toSet()
        collections.forEach { createInitialIndices(template, it) }
    }

    private fun createInitialIndices(template: MongockTemplate, collection: String) {
        val indexOps = template.indexOps(collection)
        indexOps.ensureIndex(
            Index()
                .on("transactionHash", Sort.Direction.ASC)
                .on("topic", Sort.Direction.ASC)
                .on("index", Sort.Direction.ASC)
                .on("minorLogIndex", Sort.Direction.ASC)
                .on("visible", Sort.Direction.ASC)
                .named(VISIBLE_INDEX_NAME)
                .background()
                .unique()
                .partial(PartialIndexFilter.of(Document("visible", true)))
        )

        indexOps.ensureIndex(
            Index()
                .on("transactionHash", Sort.Direction.ASC)
                .on("blockHash", Sort.Direction.ASC)
                .on("logIndex", Sort.Direction.ASC)
                .on("minorLogIndex", Sort.Direction.ASC)
                .named("transactionHash_1_blockHash_1_logIndex_1_minorLogIndex_1")
                .background()
                .unique()
        )

        indexOps.ensureIndex(
            Index()
                .on("status", Sort.Direction.ASC)
                .named("status")
                .background()
        )

        indexOps.ensureIndex(
            Index()
                .on("blockHash", Sort.Direction.ASC)
                .named("blockHash")
                .background()
        )
    }

    companion object {
        const val VISIBLE_INDEX_NAME = "transactionHash_1_topic_1_index_1_minorLogIndex_1_visible_1"
    }
}