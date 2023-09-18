package com.rarible.ethereum.monitoring

import io.micrometer.core.instrument.ImmutableTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class EventCountMetrics(val meterRegistry: MeterRegistry) {

    private val gaugeMetricCache: MutableMap<GaugeMetricKey, AtomicInteger> = ConcurrentHashMap()

    fun eventReceivedGauge(stage: Stage, blockchain: String, eventType: EventType) =
        gaugeMetricCache.computeIfAbsent(GaugeMetricKey(EVENT_RECEIVED_METRIC, stage, blockchain, eventType)) {
            gauge(EVENT_RECEIVED_METRIC, stage, blockchain, eventType)
        }

    fun eventReceived(stage: Stage, blockchain: String, eventType: EventType, count: Int = 1) {
        eventReceivedGauge(stage, blockchain, eventType).addAndGet(count)
    }

    fun eventSentGauge(stage: Stage, blockchain: String, eventType: EventType) =
        gaugeMetricCache.computeIfAbsent(GaugeMetricKey(EVENT_RECEIVED_METRIC, stage, blockchain, eventType)) {
            gauge(
                EVENT_SENT_METRIC,
                stage,
                blockchain,
                eventType
            )
        }

    fun eventSent(stage: Stage, blockchain: String, eventType: EventType, count: Int = 1) {
        eventSentGauge(stage, blockchain, eventType).addAndGet(count)
    }

    private fun gauge(
        metricName: String,
        stage: Stage,
        blockchain: String,
        eventType: EventType
    ): AtomicInteger {
        val i = AtomicInteger(0)
        meterRegistry.gauge(
            metricName,
            listOf(
                tag("blockchain", blockchain),
                tag("stage", stage.name.lowercase()),
                tag("type", eventType.name.lowercase()),
            ),
            i,
            AtomicInteger::toDouble
        )
        return i
    }

    private fun tag(key: String, value: String): Tag {
        return ImmutableTag(key, value)
    }

    enum class Stage {
        INDEXER, INDEXER_INTERNAL
    }

    enum class EventType {
        ERC20,
        ORDER,
        AUCTION,
        ACTIVITY,
        ITEM,
        COLLECTION,
        OWNERSHIP
    }

    private data class GaugeMetricKey(
        val metricName: String,
        val stage: Stage,
        val blockchain: String,
        val eventType: EventType
    )

    companion object {
        const val EVENT_RECEIVED_METRIC = "event_received"
        const val EVENT_SENT_METRIC = "event_sent"
    }
}
