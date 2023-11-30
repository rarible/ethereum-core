package com.rarible.ethereum.client

import io.daonomic.rpc.mono.WebClientTransport
import scalether.transport.WebSocketPubSubTransport

class LegacyEthereumTransportProvider(
    node: EthereumNode,
    requestTimeoutMs: Int,
    readWriteTimeoutMs: Int,
    maxFrameSize: Int,
    retryMaxAttempts: Long,
    retryBackoffDelay: Long,
) :
    EthereumTransportProvider() {
    private val webSocketPubSubTransport = WebSocketPubSubTransport(node.wsUrl, maxFrameSize)
    private val rpcTransport =
        httpTransport(
            httpUrl = node.rpcUrl,
            requestTimeoutMs = requestTimeoutMs,
            readWriteTimeoutMs = readWriteTimeoutMs,
            maxFrameSize = maxFrameSize,
            retryMaxAttempts = retryMaxAttempts,
            retryBackoffDelay = retryBackoffDelay,
        )

    init {
        logger.info("Will use legacy ethereum transport")
    }

    override fun websocketDisconnected() {
    }

    override fun rpcError() {
    }

    override fun registerWebsocketSubscription(reconnect: () -> Unit) {
    }

    override fun unregisterWebsocketSubscription(reconnect: () -> Unit) {
    }

    override suspend fun getWebsocketTransport(): WebSocketPubSubTransport = webSocketPubSubTransport

    override suspend fun getRpcTransport(): WebClientTransport = rpcTransport

    override suspend fun getFailoverRpcTransport(): WebClientTransport? = null
}
