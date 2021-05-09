package com.rarible.ethereum.monitoring

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.core.daemon.sequential.SequentialDaemonWorker
import com.rarible.ethereum.domain.Blockchain
import com.rarible.ethereum.listener.log.domain.BlockHead
import com.rarible.ethereum.listener.log.domain.BlockStatus
import com.rarible.ethereum.listener.log.persist.BlockRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.time.delay
import java.time.Instant
import kotlin.math.max

class BlockchainMonitoringWorker(
    properties: DaemonWorkerProperties,
    private val blockchain: Blockchain,
    private val meterRegistry: MeterRegistry,
    private val blockRepository: BlockRepository
) : SequentialDaemonWorker(meterRegistry, properties) {

    @Volatile private var lastSeenBlockHead: BlockHead? = null
    @Volatile private var erroBlocksCount: Long? = null
    @Volatile private var pendingBlocksCount: Long? = null

    init {
        registerGauge()
        start()
    }

    override suspend fun handle() {
        lastSeenBlockHead = blockRepository.findFirstByIdDesc().awaitFirst()
        erroBlocksCount = blockRepository.findByStatus(BlockStatus.ERROR).count().awaitFirst()
        pendingBlocksCount = blockRepository.findByStatus(BlockStatus.PENDING).count().awaitFirst()

        delay(pollingPeriod)
    }

    private fun getBlockDelay(): Double? {
        val lastSeenBlockTimestamp = lastSeenBlockHead?.timestamp ?: return null
        val currentTimestamp = Instant.now().epochSecond
        return max(currentTimestamp - lastSeenBlockTimestamp, 0).toDouble()
    }

    private fun getErrorBlockCount(): Double {
        return (erroBlocksCount ?: 0).toDouble()
    }

    private fun getPendingBlockCount(): Double {
        return (pendingBlocksCount ?: 0).toDouble()
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
    }
}