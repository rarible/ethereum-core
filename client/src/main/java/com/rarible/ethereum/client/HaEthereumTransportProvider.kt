package com.rarible.ethereum.client

import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import scala.collection.immutable.Map
import scala.collection.immutable.Map.from
import scala.jdk.CollectionConverters
import scalether.core.MonoEthereum
import scalether.transport.WebSocketPubSubTransport
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class HaEthereumTransportProvider(
    private val localNodes: List<EthereumNode>,
    private val externalNodes: List<EthereumNode>,
    private val requestTimeoutMs: Int,
    private val readWriteTimeoutMs: Int,
    private val maxFrameSize: Int,
    private val retryMaxAttempts: Long,
    private val retryBackoffDelay: Long,
    private val monitoringThreadInterval: Duration,
    private val maxBlockDelay: Duration,
) : AutoCloseable,
    EthereumTransportProvider() {
    private val websocketNode: AtomicReference<EthereumTransport> = AtomicReference()
    private val rpcNode: AtomicReference<EthereumTransport> = AtomicReference()
    private val websocketSubscriptions = CopyOnWriteArrayList<() -> Unit>()
    private val monitoringThread = MonitoringThread()

    init {
        logger.info("Will use HA ethereum transport provider. localNodes=$localNodes, externalNodes=$externalNodes")
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

    override suspend fun getFailoverRpcTransport(): WebClientTransport? =
        if (externalNodes.isNotEmpty() && rpcNode.get()?.let { it.node !in externalNodes } == true) {
            createHttpTransport(externalNodes.first())
        } else {
            null
        }

    private suspend fun aliveNode(
        nodes: List<EthereumNode> = localNodes + externalNodes
    ): EthereumTransport {
        logger.info("Will check nodes: $nodes")
        for (node in nodes) {
            logger.info("Checking node definition $node")
            val httpTransport = object : WebClientTransport(
                node.rpcUrl,
                MonoEthereum.mapper(),
                requestTimeoutMs,
                readWriteTimeoutMs
            ) {
                override fun headers() = defaultHeaders(node) ?: super.headers()
            }
            if (nodeAvailable(node.rpcUrl, httpTransport)) {
                logger.info("Node $node is available. Will use it")
                return EthereumTransport(
                    rpcTransport = createHttpTransport(node),
                    websocketTransport = WebSocketPubSubTransport(node.wsUrl, maxFrameSize),
                    node = node,
                )
            }
        }
        throw IllegalStateException("None of nodes $nodes are available")
    }

    private fun defaultHeaders(node: EthereumNode): Map<String, String>? = node.rpcAuth?.let {
        from(
            CollectionConverters.MapHasAsScala(
                mapOf(
                    "Authorization" to createBasicAuthHeader(
                        it.first,
                        it.second
                    )
                )
            ).asScala()
        )
    }
    private fun createBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val base64Credentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $base64Credentials"
    }

    private fun createHttpTransport(node: EthereumNode) = httpTransport(
        httpUrl = node.rpcUrl,
        headers = defaultHeaders(node),
        requestTimeoutMs = requestTimeoutMs,
        readWriteTimeoutMs = readWriteTimeoutMs,
        maxFrameSize = maxFrameSize,
        retryMaxAttempts = retryMaxAttempts,
        retryBackoffDelay = retryBackoffDelay,
    )

    private suspend fun nodeAvailable(rpcUrl: String, rpcTransport: WebClientTransport): Boolean {
        val attempt = AtomicLong(0)
        while (attempt.getAndIncrement() < retryMaxAttempts) {
            try {
                val ethereum = MonoEthereum(rpcTransport)
                val currentBlockNumber = ethereum.ethBlockNumber().awaitSingle()
                val block = ethereum.ethGetBlockByNumber(currentBlockNumber).awaitFirstOrNull()
                if (block == null) {
                    logger.warn("Can't get block by number for node $rpcUrl, retry...")
                    delay(retryBackoffDelay)
                    continue
                }
                val timestamp = Instant.ofEpochSecond(block.timestamp().toLong())
                val now = Instant.now()
                val result = now.epochSecond - timestamp.epochSecond < maxBlockDelay.seconds
                if (!result) {
                    logger.warn(
                        "Node {} is not available. Last block is too old. block: {}, timestamp: {}, now: {}",
                        rpcUrl, currentBlockNumber, timestamp, now
                    )
                }
                return result
            } catch (ex: Throwable) {
                logger.warn("Error while calling node {}. Trying next node...", rpcUrl, ex)
                break
            }
        }
        return false
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
                sleep(monitoringThreadInterval.toMillis())
            }
        }

        private suspend fun checkNode() {
            val nodeInUse = websocketNode.get() ?: rpcNode.get() ?: return
            if (nodeInUse.node in externalNodes) {
                val node = aliveNode(localNodes)
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
        val node: EthereumNode,
    )
}
