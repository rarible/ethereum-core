package com.rarible.ethereum.domain

// This class is used if for backward compatibility
data class Blockchain(val blockchain: String) {

    val value: String
        get() = blockchain.lowercase()

    val name: String
        get() = blockchain.uppercase()
}
