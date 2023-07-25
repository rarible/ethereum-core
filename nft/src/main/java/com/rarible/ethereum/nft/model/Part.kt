package com.rarible.ethereum.nft.model

import com.rarible.ethereum.common.Tuples
import com.rarible.ethereum.common.keccak256
import io.daonomic.rpc.domain.Word
import scala.Tuple3
import scalether.domain.Address

data class Part(
    val account: Address,
    val value: Int
) {
    fun hash(): Word = keccak256(
        Tuples.partHashType().encode(
            Tuple3(
                TYPE_HASH.bytes(),
                account,
                value.toBigInteger()
            )
        )
    )

    companion object {
        private val TYPE_HASH: Word = keccak256("Part(address account,uint96 value)")
    }
}
