package com.rarible.ethereum.client

import java.net.URI
import java.net.URL

data class EthereumNode(
    val httpUrl: String,
    val websocketUrl: String,
) {
    private val fullRpcUrl = URL(httpUrl)
    private val fullWsUrl = URI(websocketUrl)

    val rpcUrl = URL(fullRpcUrl.protocol, fullRpcUrl.host, fullRpcUrl.port, fullRpcUrl.file).toString()

    val wsUrl = URI(
        fullWsUrl.scheme,
        null,
        fullWsUrl.host,
        fullWsUrl.port,
        fullWsUrl.path,
        fullWsUrl.query,
        fullWsUrl.fragment
    ).toString()

    val basicAuth: String?
        get() = fullRpcUrl.userInfo
}
