package com.rarible.ethereum.common

import com.rarible.rpc.domain.Bytes
import com.rarible.rpc.domain.Word
import org.web3j.crypto.Hash
import scalether.domain.Address
import scalether.util.Hex
import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return Hex.prefixed(this.toByteArray())
}

fun String.toAddress(): Address {
    return Address.apply(this)
}

fun keccak256(bytes: ByteArray): Word = Word(Hash.sha3(bytes))

fun keccak256(bytes: Bytes): Word = keccak256(bytes.bytes())

fun keccak256(str: String): Word = keccak256(str.toByteArray(Charsets.US_ASCII))