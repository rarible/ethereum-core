package com.rarible.ethereum.nft.misc

import com.rarible.ethereum.common.keccak256
import io.daonomic.rpc.domain.Bytes

fun List<Bytes>.hash(): ByteArray = keccak256(fold(ByteArray(0)) { acc, next -> acc + next.bytes() }).bytes()
