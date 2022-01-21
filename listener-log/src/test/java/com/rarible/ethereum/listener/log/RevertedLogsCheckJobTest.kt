package com.rarible.ethereum.listener.log

import com.rarible.ethereum.listener.log.data.DummyData1
import com.rarible.ethereum.listener.log.data.DummyData2
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.domain.CheckedBlock
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.mock.randomLogEvent
import com.rarible.ethereum.listener.log.mock.randomWordd
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.ethereum.listener.log.persist.RevertedLogStateRepository
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
internal class RevertedLogsCheckJobTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var revertedLogsCheckJob: RevertedLogsCheckJob

    @Autowired
    private lateinit var logEventRepository: LogEventRepository

    @Autowired
    private lateinit var blockRepository: BlockRepository

    @Autowired
    private lateinit var revertedLogStateRepository: RevertedLogStateRepository

    @Autowired
    private lateinit var logEventDescriptorDummyData1: LogEventDescriptor<DummyData1>

    @Autowired
    private lateinit var logEventDescriptorDummyData2: LogEventDescriptor<DummyData2>

    @Test
    fun `should check block with reverted block as error`() = runBlocking<Unit> {
        val lastBlock = BlockHead(
            id = 10,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        val block1 = BlockHead(
            id = 9,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        val block2 = BlockHead(
            id = 8,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        val block3 = BlockHead(
            id = 7,
            hash = randomWordd(),
            timestamp = 0,
            status = BlockStatus.SUCCESS
        )
        blockRepository.save(lastBlock)
        blockRepository.save(block1)
        blockRepository.save(block2)
        blockRepository.save(block3)
        revertedLogStateRepository.save(CheckedBlock(block3.id))

        val topic = randomWordd()
        val log1 = randomLogEvent(topic).copy(blockNumber = block1.id, status = LogEventStatus.REVERTED)
        val log2 = randomLogEvent(topic).copy(blockNumber = block2.id, status = LogEventStatus.CONFIRMED)
        val log3 = randomLogEvent(topic).copy(blockNumber = block3.id, status = LogEventStatus.REVERTED)
        logEventRepository.save(logEventDescriptorDummyData1.collection, log1).awaitFirst()
        logEventRepository.save(logEventDescriptorDummyData1.collection, log2).awaitFirst()
        logEventRepository.save(logEventDescriptorDummyData2.collection, log3).awaitFirst()

        revertedLogsCheckJob.job()

        assertThat(blockRepository.findById(lastBlock.id)?.status).isEqualTo(BlockStatus.SUCCESS)
        assertThat(blockRepository.findById(block1.id)?.status).isEqualTo(BlockStatus.ERROR)
        assertThat(blockRepository.findById(block2.id)?.status).isEqualTo(BlockStatus.SUCCESS)
        assertThat(blockRepository.findById(block3.id)?.status).isEqualTo(BlockStatus.ERROR)
    }
}