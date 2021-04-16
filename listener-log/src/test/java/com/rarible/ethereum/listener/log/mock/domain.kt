package com.rarible.ethereum.listener.log.mock

import com.rarible.ethereum.listener.log.domain.EventData
import scalether.domain.Address
import java.math.BigInteger

data class Transfer(
    val from: Address,
    val to: Address,
    val value: BigInteger
) : EventData