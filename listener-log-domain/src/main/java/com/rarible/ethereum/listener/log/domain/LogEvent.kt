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
     * Hash of the transaction.
     * Note that the transaction may be pending, in which case the [blockHash], [blockNumber] and [logIndex] are `null`.
     */
    val transactionHash: Word,
    /**
     * Status of the log event. Usually, only [LogEventStatus.CONFIRMED] log events impact business calculations.
     */
    val status: LogEventStatus,
    /**
     * Hash of the block inside which this log event was produced, or `null` for pending logs.
     */
    val blockHash: Word? = null,
    /**
     * Number of the block inside which this log event was produced, or `null` for pending logs.
     */
    val blockNumber: Long? = null,
    /**
     * Index of this log event inside the whole block, or `null` for pending logs.
     * This is a native Ethereum value.
     */
    val logIndex: Int? = null,
    /**
     * Sender of the transaction.
     *
     * This field is nullable until all log events are updated in the database.
     */
    val from: Address? = null,
    /**
     * Receiver of the transaction.
     */
    val to: Address? = null,
    /**
     * Timestamp of the block to which this log event belongs.
     *
     * This field is nullable until all log events are updated in the database.
     */
    val blockTimestamp: Long? = null,
    /**
     * Secondary index of this log event among all logs produced by `LogEventDescriptor.convert` for the same log
     * with exactly the same [blockNumber], [blockHash], [transactionHash], [logIndex] and [index].
     * The [minorLogIndex] is used to distinguish consequent business events.
     */
    val minorLogIndex: Int,
    /**
     * 0-based index of this log event among logs of the same transaction and having the same topic and coming from the same set of listened addresses.
     * It is different from [logIndex] in that the [logIndex] is per-block but [index] is per-transaction-per-topic-per-set-of-addresses.
     * Note that [logIndex] is a commonly used index (defined in Ethereum spec), whereas the [index] is calculated by our code.
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
    val updatedAt: Instant = createdAt,
    val dbUpdatedAt: Instant = updatedAt
) : Identifiable<ObjectId> {
    fun withDbUpdated(): LogEvent {
        return copy(dbUpdatedAt = Instant.now())
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
interface EventData
