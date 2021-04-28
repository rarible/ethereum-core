package com.rarible.ethereum.block

import io.daonomic.rpc.domain.Bytes

data class BlockEvent<B : Block>(
    val block: B,
    val reverted: BlockInfo? = null
)

data class BlockInfo(
    val hash: Bytes,
    val number: Long
)