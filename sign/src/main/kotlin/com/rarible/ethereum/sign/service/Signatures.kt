package com.rarible.ethereum.sign.service

import io.daonomic.rpc.domain.Binary
import java.nio.charset.StandardCharsets

object Signatures {
    internal const val START = "\u0019Ethereum Signed Message:\n"

    fun addStart(message: String): Binary {
        val resultMessage = START + message.toByteArray(StandardCharsets.UTF_8).size + message
        return Binary.apply(resultMessage.toByteArray(StandardCharsets.UTF_8))
    }
}
