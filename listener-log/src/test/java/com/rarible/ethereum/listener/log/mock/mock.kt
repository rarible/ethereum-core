package com.rarible.ethereum.listener.log.mock

import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomWord
import com.rarible.ethereum.listener.log.domain.EventData
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import io.daonomic.rpc.domain.Word
import java.time.Instant

fun randomWordd(): Word = Word.apply(randomWord())

fun randomLogEvent(topic: Word = randomWordd()): LogEvent =
    LogEvent(
        blockNumber = 0,
        blockHash = randomWordd(),
        transactionHash = randomWordd(),
        address = randomAddress(),
        topic = topic,

        logIndex = 0,
        index = 0,
        minorLogIndex = 0,

        status = LogEventStatus.CONFIRMED,

        data = object : EventData {},
        visible = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
