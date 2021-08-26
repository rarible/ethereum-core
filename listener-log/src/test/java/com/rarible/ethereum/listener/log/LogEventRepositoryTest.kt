package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.domain.EventData
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import io.daonomic.rpc.domain.Word
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import scalether.domain.Address
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

class LogEventRepositoryTest : AbstractIntegrationTest() {
    private lateinit var logEventRepository: LogEventRepository

    @BeforeEach
    fun setup() {
        logEventRepository = LogEventRepository(mongo)
    }

    @Test
    fun `should find and delete reverted log`() {
        val collection = "transfer"

        val targetBlockHash = ByteArray(32).let {
            ThreadLocalRandom.current().nextBytes(it)
            Word.apply(it)
        }
        val topic = Word.apply(RandomUtils.nextBytes(32))
        val confirmedEventLog = createLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.CONFIRMED)
        val revertedEventLog = createLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.REVERTED)

        logEventRepository.save(collection, confirmedEventLog).block()
        logEventRepository.save(collection, revertedEventLog).block()

        val deletedRevertedBlock = logEventRepository.findAndDelete(collection, targetBlockHash, topic, LogEventStatus.REVERTED).collectList().block()
        assertThat(deletedRevertedBlock?.single()?.id).isEqualTo(revertedEventLog.id)

        val savedConfirmedLog = logEventRepository.findLogEvent(collection, confirmedEventLog.id).block()
        assertThat(savedConfirmedLog).isNotNull

        val savedRevertedLog = logEventRepository.findLogEvent(collection, revertedEventLog.id).block()
        assertThat(savedRevertedLog).isNull()
    }

    @Test
    fun `should find log by index and minorLogIndex`() {
        val collection = "transfer"

        val transactionHash = ByteArray(32).let {
            ThreadLocalRandom.current().nextBytes(it)
            Word.apply(it)
        }
        val topic = Word.apply(RandomUtils.nextBytes(32))
        val eventLog1 = createLogEvent(topic).copy(transactionHash = transactionHash, index = 0, minorLogIndex = 1)
        val eventLog2 = createLogEvent(topic).copy(transactionHash = transactionHash, index = 1, minorLogIndex = 2)

        logEventRepository.save(collection, eventLog1).block()
        logEventRepository.save(collection, eventLog2).block()

        val savedEventLog1 = logEventRepository.findVisibleByKey(collection, transactionHash, topic, index = 0, minorLogIndex = 1).block()!!
        assertThat(savedEventLog1.id).isEqualTo(eventLog1.id)

        val savedEventLog2 = logEventRepository.findVisibleByKey(collection, transactionHash, topic, index = 1, minorLogIndex = 2).block()!!
        assertThat(savedEventLog2.id).isEqualTo(eventLog2.id)
    }

    private fun createLogEvent(topic: Word): LogEvent {
        val blockHash = ByteArray(32).also {
            ThreadLocalRandom.current().nextBytes(it)
        }
        return LogEvent(
            id = ObjectId.get(),
            blockHash = Word.apply(blockHash) ,
            status = LogEventStatus.REVERTED,
            data = object : EventData { },
            address = Address.ONE(),
            topic = topic,
            transactionHash = Word.apply(blockHash),
            index = 1,
            minorLogIndex = 1,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
