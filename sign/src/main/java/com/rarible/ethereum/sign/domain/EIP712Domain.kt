package com.rarible.ethereum.sign.domain

import com.rarible.ethereum.common.Tuples
import com.rarible.ethereum.common.keccak256
import com.rarible.rpc.domain.Binary
import com.rarible.rpc.domain.Word
import scala.Tuple5
import scalether.domain.Address
import scalether.util.Hash
import java.math.BigInteger

data class EIP712Domain(
    val name: String,
    val version: String,
    val chainId: BigInteger,
    val verifyingContract: Address
) {
    fun hash(): Word =
        keccak256(Tuples.eip712DomainHashType().encode(Tuple5(
            TYPE_HASH.bytes(),
            keccak256(name).bytes(),
            keccak256(version).bytes(),
            chainId,
            verifyingContract
        )))

    fun hashToSign(structHash: Word): Word = Word(Hash.sha3(
        Binary.apply("0x1901")
            .add(hash())
            .add(structHash).bytes()))

    companion object {
        private val TYPE_HASH: Word = keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)")
    }
}