package com.rarible.ethereum.client

import io.daonomic.rpc.mono.WebClientTransport

class LegacyEthereumTransportProvider(
    node: EthereumNode,
    requestTimeoutMs: Int,
    readWriteTimeoutMs: Int,
    maxFrameSize: Int,
    retryMaxAttempts: Long,
    retryBackoffDelay: Long,
) :
    EthereumTransportProvider() {
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

    override fun rpcError() {
    }

    override suspend fun getRpcTransport(): WebClientTransport = rpcTransport

    override suspend fun getFailoverRpcTransport(): WebClientTransport? = null
}
