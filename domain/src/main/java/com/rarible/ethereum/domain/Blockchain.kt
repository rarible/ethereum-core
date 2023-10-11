package com.rarible.ethereum.domain

// value must be in lower case, example: "ethereum"
data class Blockchain(val value: String) {

    // it is used for backward compatibility
    val name: String
        get() = value.uppercase()
}
