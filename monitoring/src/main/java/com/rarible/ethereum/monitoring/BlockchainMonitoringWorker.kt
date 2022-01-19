package com.rarible.ethereum.monitoring

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.core.daemon.sequential.SequentialDaemonWorker
import com.rarible.ethereum.domain.Blockchain
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.time.delay
import java.time.Instant
import kotlin.math.max

class BlockchainMonitoringWorker(
    properties: DaemonWorkerProperties,
    private val blockchain: Blockchain,
    meterRegistry: MeterRegistry,
    private val blockRepository: BlockRepository
) : SequentialDaemonWorker(meterRegistry, properties) {

    @Volatile private var lastSeenBlockHead: BlockHead? = null
    @Volatile private var firstSeenBlockHead: BlockHead? = null
    @Volatile private var blocksCount: Long? = null
    @Volatile private var errorBlocksCount: Long? = null
    @Volatile private var pendingBlocksCount: Long? = null

    init {
        registerGauge()
        start()
    }

    override suspend fun handle() {
        firstSeenBlockHead = blockRepository.findFirstByIdAsc().awaitFirstOrNull()
        lastSeenBlockHead = blockRepository.findFirstByIdDesc().awaitFirstOrNull()
        blocksCount = blockRepository.count().awaitFirstOrNull()
        errorBlocksCount = blockRepository.findByStatus(BlockStatus.ERROR).count().awaitFirstOrNull()
        pendingBlocksCount = blockRepository.findByStatus(BlockStatus.PENDING).count().awaitFirstOrNull()

        delay(pollingPeriod)
    }

    private fun getBlockDelay(): Double? {
        val lastSeenBlockTimestamp = lastSeenBlockHead?.timestamp ?: return null
        val currentTimestamp = Instant.now().epochSecond
        return max(currentTimestamp - lastSeenBlockTimestamp, 0).toDouble()
    }

    private fun getErrorBlockCount(): Double {
        return (errorBlocksCount ?: 0).toDouble()
    }

    private fun getPendingBlockCount(): Double {
        return (pendingBlocksCount ?: 0).toDouble()
    }

    private fun getMissingBlockCount(): Double {
        val last = (lastSeenBlockHead?.id ?: 0)
        val first = (firstSeenBlockHead?.id ?: 0)
        val count = (blocksCount ?: 0)
        return (count - (last - first)).toDouble()
    }

    private fun registerGauge() {
        Gauge.builder("protocol.listener.block.delay", this::getBlockDelay)
            .tag("blockchain", blockchain.value)
            .register(meterRegistry)

        Gauge.builder("protocol.listener.block.error", this::getErrorBlockCount)
            .tag("blockchain", blockchain.value)
            .register(meterRegistry)

        Gauge.builder("protocol.listener.block.pending", this::getPendingBlockCount)
            .tag("blockchain", blockchain.value)
            .register(meterRegistry)

        Gauge.builder("protocol.listener.block.missing", this::getMissingBlockCount)
            .tag("blockchain", blockchain.value)
            .register(meterRegistry)
    }
}
