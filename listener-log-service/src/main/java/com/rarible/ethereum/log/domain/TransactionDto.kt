package com.rarible.ethereum.log.domain

import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

data class TransactionDto(
    val hash: Word,
    val from: Address,
    val to: Address?,
    val input: Binary
)