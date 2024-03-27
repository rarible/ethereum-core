package com.rarible.ethereum.client

import java.net.URL

data class EthereumNode(
    val httpUrl: String,
) {
    private val fullRpcUrl = URL(httpUrl)

    val rpcUrl = URL(fullRpcUrl.protocol, fullRpcUrl.host, fullRpcUrl.port, fullRpcUrl.file).toString()

    val basicAuth: String?
        get() = fullRpcUrl.userInfo
}
