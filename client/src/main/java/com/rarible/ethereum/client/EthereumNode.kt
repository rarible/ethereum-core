package com.rarible.ethereum.client

import java.net.URI
import java.net.URL

data class EthereumNode(
    val httpUrl: String,
    val websocketUrl: String,
) {
    private val fullRpcUrl = URL(httpUrl)
    private val fullWsUrl = URI(websocketUrl)

    val rpcUrl: String
        get() = URL(fullRpcUrl.protocol, fullRpcUrl.host, fullRpcUrl.port, fullRpcUrl.file).toString()

    val wsUrl: String
        get() = URI(
            fullWsUrl.scheme,
            null,
            fullWsUrl.host,
            fullWsUrl.port,
            fullWsUrl.path,
            fullWsUrl.query,
            fullWsUrl.fragment
        ).toString()

    val rpcAuth: Pair<String, String>?
        get() = extractBasicAuth(fullRpcUrl.userInfo)

    val wsAuth: Pair<String, String>?
        get() = extractBasicAuth(fullWsUrl.userInfo)

    private fun extractBasicAuth(userInfo: String?): Pair<String, String>? {
        val parts = userInfo?.split(":") ?: emptyList()
        return when {
            parts.size == 2 -> parts.first() to parts.last()
            else -> null
        }
    }
}
