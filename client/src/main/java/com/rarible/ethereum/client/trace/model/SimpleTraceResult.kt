package com.rarible.ethereum.client.trace.model

import io.daonomic.rpc.domain.Binary
import scalether.domain.Address
import java.math.BigInteger

data class SimpleTraceResult(
    val from: Address,
    val to: Address?,
    val input: Binary,
    val value: BigInteger?,
    val output: Binary?,
    val type: String?,
)
