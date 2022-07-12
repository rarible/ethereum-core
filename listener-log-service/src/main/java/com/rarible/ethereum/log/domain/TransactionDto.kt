package com.rarible.ethereum.log.domain

import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

@Deprecated("Don't use it any more")
data class TransactionDto(
    val hash: Word,
    val from: Address,
    val nonce: Long,
    val to: Address?,
    val input: Binary
)