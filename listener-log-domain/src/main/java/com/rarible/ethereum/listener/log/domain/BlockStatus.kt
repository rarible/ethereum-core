package com.rarible.ethereum.listener.log.domain

import com.rarible.rpc.domain.Word
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

enum class BlockStatus {
    PENDING,
    SUCCESS,
    ERROR
}

@Document(collection = "block")
data class BlockHead(
    @Id
    val id: Long,
    val hash: Word,
    val timestamp: Long,
    val status: BlockStatus = BlockStatus.PENDING
)