package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.mono.WebClientTransport
import scalether.transport.WebSocketPubSubTransport

class LegacyEthereumTransportProvider(ethereumProperties: EthereumProperties) :
    EthereumTransportProvider() {
    private val webSocketPubSubTransport =
        WebSocketPubSubTransport(ethereumProperties.websocketUrl, ethereumProperties.maxFrameSize)
    private val rpcTransport =
        httpTransport(httpUrl = ethereumProperties.httpUrl!!, ethereumProperties = ethereumProperties)

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
}
