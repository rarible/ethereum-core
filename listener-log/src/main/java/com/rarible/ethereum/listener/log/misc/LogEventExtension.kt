package com.rarible.ethereum.listener.log.misc

import com.rarible.ethereum.listener.log.domain.LogEvent

val LogEvent.eventId: String
    get() = StringBuilder().run {
        append(transactionHash)
        logIndex?.let { append("_").append(it) }
        index.let { append("_").append(it) }
        append("_").append(minorLogIndex)
        toString()
    }

