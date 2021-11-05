package com.rarible.ethereum.listener.log

import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.LockGuardInvoker
import com.github.cloudyrock.mongock.driver.api.lock.guard.invoker.VoidSupplier
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.core.test.data.randomAddress
import com.rarible.ethereum.listener.log.domain.*
import com.rarible.ethereum.listener.log.mock.randomLogEvent
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.mongock.ChangeLog00004RecalculateLogEventRaribleIndex
import com.rarible.ethereum.listener.log.mongock.ChangeLog00005MarkLogsRevertedForRevertedBlocks
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.findById
import java.util.function.Supplier

@IntegrationTest
class ChangeLog00005MarkLogsRevertedForRevertedBlocksTest : AbstractIntegrationTest() {
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
    fun `mark logs reverted for reverted blocks`() {
        val block1Id = 1L
        val block1RevertedHash = randomWordd()
        val block1Hash = randomWordd()
        mongockTemplate.save(BlockHead(id = block1Id, hash = block1Hash, timestamp = 0, status = BlockStatus.SUCCESS))

        val correctLog = randomLogEvent().copy(blockNumber = block1Id, blockHash = block1Hash)
        val revertedLog = randomLogEvent().copy(blockNumber = block1Id, blockHash = block1RevertedHash)
        saveLogs(correctLog, revertedLog)

        ChangeLog00005MarkLogsRevertedForRevertedBlocks().markLogsRevertedForRevertedBlocks(mongockTemplate, collectionName)

        val documents = mongockTemplate.getCollection(collectionName).find().toList()
        assertThat(documents.find { it["_id"] as ObjectId == correctLog.id }!!["mustBeReverted"]).isNull()
        assertThat(documents.find { it["_id"] as ObjectId == revertedLog.id }!!["mustBeReverted"] as? Boolean).isTrue()
    }

    private fun saveLogs(vararg logEvents: LogEvent) {
        logEvents.forEach {
            logEventRepository.save(collectionName, it).block()
        }
    }
}