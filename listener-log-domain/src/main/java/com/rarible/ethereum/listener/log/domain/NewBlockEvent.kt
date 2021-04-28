package com.rarible.ethereum.listener.log.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import io.daonomic.rpc.domain.Word

data class NewBlockEvent(val number: Long, val hash: Word, val timestamp: Long, val reverted: Word? = null) {
    @get:JsonIgnore
    val contextParams: Map<String, String>
        get() = mapOf(
            "blockNumber" to number.toString(),
            "blockHash" to hash.toString(),
            "eventType" to "newBlock"
        ) + if (reverted != null) mapOf("reverted" to reverted.toString()) else emptyMap()
}