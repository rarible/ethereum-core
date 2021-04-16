package com.rarible.ethereum.log.domain

import com.rarible.rpc.domain.Binary
import com.rarible.rpc.domain.Word
import scalether.domain.Address

data class TransactionDto(
    val hash: Word,
    val from: Address,
    val nonce: Long,
    val to: Address?,
    val input: Binary
)