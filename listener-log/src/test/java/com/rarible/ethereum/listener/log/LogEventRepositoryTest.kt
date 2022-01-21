package com.rarible.ethereum.listener.log

import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomLong
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.mock.randomLogEvent
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadLocalRandom

@IntegrationTest
class LogEventRepositoryTest : AbstractIntegrationTest() {
    private lateinit var logEventRepository: LogEventRepository

    @BeforeEach
    fun setup() {
        logEventRepository = LogEventRepository(mongo)
    }

    @Test
    fun `should find and revert log`() {
        val collection = "transfer"

        val blockNumber = randomLong()
        val targetBlockHash = randomWordd()
        val topic = randomWordd()
        val confirmedEventLog =
            randomLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.CONFIRMED, blockNumber = blockNumber)
        val inactiveEventLog = randomLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.INACTIVE, blockNumber = blockNumber)

        logEventRepository.save(collection, confirmedEventLog).block()
        logEventRepository.save(collection, inactiveEventLog).block()

        val revertedEvents = logEventRepository.findAndRevert(collection, blockNumber, randomWordd(), topic).collectList().block()!!
        assertThat(revertedEvents.map { it.id }).containsExactlyInAnyOrder(confirmedEventLog.id, inactiveEventLog.id)

        val savedConfirmedLog = logEventRepository.findLogEvent(collection, confirmedEventLog.id).block()!!
        assertThat(savedConfirmedLog.status).isEqualTo(LogEventStatus.REVERTED)

        val savedRevertedLog = logEventRepository.findLogEvent(collection, inactiveEventLog.id).block()!!
        assertThat(savedRevertedLog.status).isEqualTo(LogEventStatus.REVERTED)
    }

    @Test
    fun `should find and delete reverted log`() {
        val collection = "transfer"

        val targetBlockHash = ByteArray(32).let {
            ThreadLocalRandom.current().nextBytes(it)
            Word.apply(it)
        }
        val topic = Word.apply(RandomUtils.nextBytes(32))
        val confirmedEventLog =
            randomLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.CONFIRMED)
        val revertedEventLog = randomLogEvent(topic).copy(blockHash = targetBlockHash, status = LogEventStatus.REVERTED)

        logEventRepository.save(collection, confirmedEventLog).block()
        logEventRepository.save(collection, revertedEventLog).block()

        val deletedRevertedBlock =
            logEventRepository.findAndDelete(collection, targetBlockHash, topic, LogEventStatus.REVERTED).collectList()
                .block()
        assertThat(deletedRevertedBlock?.single()?.id).isEqualTo(revertedEventLog.id)

        val savedConfirmedLog = logEventRepository.findLogEvent(collection, confirmedEventLog.id).block()
        assertThat(savedConfirmedLog).isNotNull

        val savedRevertedLog = logEventRepository.findLogEvent(collection, revertedEventLog.id).block()
        assertThat(savedRevertedLog).isNull()
    }

    @Test
    fun `should find visible log`() {
        val collection = "transfer"

        val transactionHash = ByteArray(32).let {
            ThreadLocalRandom.current().nextBytes(it)
            Word.apply(it)
        }
        val topic = Word.apply(RandomUtils.nextBytes(32))
        val address = randomAddress()
        val eventLog1 = randomLogEvent(topic).copy(transactionHash = transactionHash, address = address, index = 0, minorLogIndex = 1)
        val eventLog2 = randomLogEvent(topic).copy(transactionHash = transactionHash, address = address, index = 1, minorLogIndex = 2)
        val eventLog3 = randomLogEvent(topic).copy(transactionHash = transactionHash, address = randomAddress(), index = 1, minorLogIndex = 2)

        logEventRepository.save(collection, eventLog1).block()
        logEventRepository.save(collection, eventLog2).block()
        logEventRepository.save(collection, eventLog3).block()

        val savedEventLog1 = logEventRepository.findVisibleByKey(
            collection = collection,
            transactionHash = transactionHash,
            address = address,
            topic = topic,
            index = 0,
            minorLogIndex = 1
        ).block()!!
        assertThat(savedEventLog1.id).isEqualTo(eventLog1.id)

        val savedEventLog2 = logEventRepository.findVisibleByKey(
            collection = collection,
            transactionHash = transactionHash,
            topic = topic,
            address = address,
            index = 1,
            minorLogIndex = 2
        ).block()!!
        assertThat(savedEventLog2.id).isEqualTo(eventLog2.id)
    }

    @Test
    fun `should check reverted log by block number`() = runBlocking<Unit> {
        val collection = "transfer"

        val topic = Word.apply(RandomUtils.nextBytes(32))
        val eventLog1 = randomLogEvent(topic).copy(blockNumber = 10, status = LogEventStatus.REVERTED)
        val eventLog2 = randomLogEvent(topic).copy(blockNumber = 11, status = LogEventStatus.CONFIRMED)

        logEventRepository.save(collection, eventLog1).awaitFirst()
        logEventRepository.save(collection, eventLog2).awaitFirst()

        assertThat(logEventRepository.hasRevertedLogEvent(collection, 10)).isTrue()
        assertThat(logEventRepository.hasRevertedLogEvent(collection, 11)).isFalse()
    }
}
