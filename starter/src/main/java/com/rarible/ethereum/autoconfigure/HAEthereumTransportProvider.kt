package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import scalether.core.MonoEthereum
import scalether.transport.WebSocketPubSubTransport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class HAEthereumTransportProvider(private val ethereumProperties: EthereumProperties) : AutoCloseable,
    EthereumTransportProvider() {
    private val websocketNode: AtomicReference<EthereumTransport> = AtomicReference()
    private val rpcNode: AtomicReference<EthereumTransport> = AtomicReference()
    private val websocketSubscriptions = CopyOnWriteArrayList<() -> Unit>()
    private val monitoringThread = MonitoringThread()

    init {
        logger.info("Will use HA ethereum transport provider")
        monitoringThread.start()
    }

    /**
     * In case websocket is disconnected we rediscover new node
     */
    override fun websocketDisconnected() {
        websocketNode.set(null)
        rpcNode.set(null)
    }

    /**
     * In case rpc error and there is a websocket connection we still use websocket connection. If there is no
     * websocket connection we wil rediscover
     */
    override fun rpcError() {
        rpcNode.set(websocketNode.get())
    }

    override fun registerWebsocketSubscription(reconnect: () -> Unit) {
        websocketSubscriptions.add(reconnect)
    }

    override fun unregisterWebsocketSubscription(reconnect: () -> Unit) {
        websocketSubscriptions.removeIf { it == reconnect }
    }

    override suspend fun getWebsocketTransport(): WebSocketPubSubTransport {
        val cachedNode = websocketNode.get()
        if (cachedNode == null) {
            val aliveNode = aliveNode()
            websocketNode.set(aliveNode)
            rpcNode.set(aliveNode)
            return aliveNode.websocketTransport
        }
        return cachedNode.websocketTransport
    }

    override suspend fun getRpcTransport(): WebClientTransport {
        // Always prefer current websocket connection over rpc
        val cachedNode = websocketNode.get() ?: rpcNode.get()
        if (cachedNode == null) {
            val aliveNode = aliveNode()
            // Could be updated also from getWebsocketTransport. We prefer one from websocket
            val nodeToUse = rpcNode.accumulateAndGet(aliveNode) { prev, next ->
                prev ?: next
            }
            return nodeToUse.rpcTransport
        }
        return cachedNode.rpcTransport
    }

    private suspend fun aliveNode(
        nodes: List<NodeProperty> = ethereumProperties.nodes + ethereumProperties.externalNodes
    ): EthereumTransport {
        logger.info("Will check nodes: $nodes")
        for (node in nodes) {
            logger.info("Using new node definition httpUrl=${node.httpUrl}, websocketUrl=${node.websocketUrl}")
            val httpTransport = WebClientTransport(
                node.httpUrl,
                MonoEthereum.mapper(),
                ethereumProperties.requestTimeoutMs,
                ethereumProperties.readWriteTimeoutMs
            )
            if (nodeAvailable(node.httpUrl, httpTransport)) {
                return EthereumTransport(
                    rpcTransport = httpTransport(node.httpUrl, ethereumProperties),
                    websocketTransport = WebSocketPubSubTransport(node.websocketUrl, ethereumProperties.maxFrameSize),
                    node = node,
                )
            }
        }
        throw IllegalStateException("None of nodes ${ethereumProperties.nodes} are available")
    }

    private suspend fun nodeAvailable(rpcUrl: String, rpcTransport: WebClientTransport): Boolean =
        try {
            MonoEthereum(rpcTransport).netListening().awaitSingle() as Boolean
        } catch (e: Exception) {
            logger.warn("Error while calling node {}. Trying next node...", rpcUrl, e)
            false
        }

    override fun close() {
        monitoringThread.close()
    }

    inner class MonitoringThread : Thread() {
        @Volatile
        private var shouldRun: Boolean = true

        override fun run() {
            while (shouldRun) {
                try {
                    runBlocking {
                        checkNode()
                    }
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error in monitoring thread: ${e.message}", e)
                }
                sleep(ethereumProperties.monitoringThreadInterval.toMillis())
            }
        }

        private suspend fun checkNode() {
            val nodeInUse = websocketNode.get() ?: rpcNode.get() ?: return
            if (nodeInUse.node in ethereumProperties.externalNodes) {
                val node = aliveNode(ethereumProperties.nodes)
                logger.info("Found alive node ${node.node}")
                rpcNode.set(node)
                websocketNode.accumulateAndGet(node) { prev, next ->
                    if (prev != null) {
                        next
                    } else {
                        null
                    }
                }
                websocketSubscriptions.forEach { it() }
            }
        }

        fun close() {
            shouldRun = false
            interrupt()
        }
    }

    data class EthereumTransport(
        val rpcTransport: WebClientTransport,
        val websocketTransport: WebSocketPubSubTransport,
        val node: NodeProperty,
    )
}
