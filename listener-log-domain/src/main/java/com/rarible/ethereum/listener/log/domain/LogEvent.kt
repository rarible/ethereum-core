package com.rarible.ethereum.listener.log.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.rarible.core.common.Identifiable
import io.daonomic.rpc.domain.Word
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import scalether.domain.Address
import java.time.Instant

/**
 * Log event emitted by a smart contract and subject for business processing.
 * Such [LogEvent]s are filled with [data] created by [com.rarible.ethereum.listener.log.LogEventDescriptor]s.
 * They are saved to dedicated repositories, from which are queried and processed by upper level code (such as reducing services).
 */
data class LogEvent(
    /**
     * Arbitrary business data associated with this log event. This is produced by `LogEventDescriptor`.
     */
    val data: EventData,
    /**
     * Address of the smart contract that produced this log event.
     */
    val address: Address,
    /**
     * ID of the log event as defined by Ethereum log (e.g. `keccak256("SomeLogEvent(address,unit256)")`).
     */
    val topic: Word,
    /**
     * Hash of the transaction inside block #[blockNumber].
     */
    val transactionHash: Word,
    /**
     * Status of the log event. Usually, only [LogEventStatus.CONFIRMED] log events impact business calculations.
     */
    val status: LogEventStatus,
    /**
     * Hash of the block inside which this log event was produced.
     */
    val blockHash: Word? = null,
    /**
     * Number of the block inside which this log event was produced.
     */
    val blockNumber: Long? = null,
    /**
     * Index of this log event inside the whole block.
     * This is a native Ethereum value.
     */
    val logIndex: Int? = null,
    /**
     * Secondary index of this log event among all logs produced by `LogEventDescriptor.convert` for the same log
     * with exactly the same [blockNumber], [blockHash], [transactionHash], [logIndex] and [index].
     * The [minorLogIndex] is used to distinguish consequent events.
     */
    val minorLogIndex: Int,
    /**
     * Index of this log event inside the transaction in which it was produced.
     * It is different from [logIndex] in that the [logIndex] is per-block but [index] is per-transaction.
     * Also note that [logIndex] is a commonly used index (defined in Ethereum spec), whereas the [index] is calculated by our code.
     */
    val index: Int,
    /**
     * Whether this log event should be considered for processing.
     */
    val visible: Boolean = true,
    @Id
    override val id: ObjectId = ObjectId.get(),
    @Version
    val version: Long? = null,

    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH
) : Identifiable<ObjectId>

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
interface EventData
