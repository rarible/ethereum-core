package com.rarible.ethereum.listener.log.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.rarible.core.common.Identifiable
import io.daonomic.rpc.domain.Word
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import scalether.domain.Address
import java.time.Instant

data class LogEvent(
    val data: EventData,
    val address: Address,
    val topic: Word,
    val transactionHash: Word,
    val from: Address? = null,
    val nonce: Long? = null,
    val status: LogEventStatus,
    val blockHash: Word? = null,
    val blockNumber: Long? = null,
    val logIndex: Int? = null,
    val minorLogIndex: Int,
    val index: Int,
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
