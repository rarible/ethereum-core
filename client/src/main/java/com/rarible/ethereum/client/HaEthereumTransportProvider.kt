package com.rarible.ethereum.client

import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Error
import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpHeaders
import scala.Option
import scala.collection.immutable.Map
import scala.collection.immutable.Map.from
import scala.jdk.CollectionConverters
import scalether.core.MonoEthereum
import java.time.Duration
import java.time.Instant
import java.util.Base64
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
    private val rpcNode: AtomicReference<EthereumTransport> = AtomicReference()
    private val monitoringThread = MonitoringThread()

    init {
        logger.info("Will use HA ethereum transport provider. localNodes=$localNodes, externalNodes=$externalNodes")
        monitoringThread.start()
    }

    override fun rpcError() {
        rpcNode.set(null)
    }

    override suspend fun getRpcTransport(): WebClientTransport {
        val cachedNode = rpcNode.get()
        if (cachedNode == null) {
            val aliveNode = aliveNode()
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
                    node = node,
                )
            }
        }
        val message = "None of nodes $nodes are available"
        // In most cases ethereum RPC client throws RpcCodeException, so here we throw similar exception
        // in order to allow upper services to distinguish fail reasons
        throw RpcCodeException(message, Error(0, message, Option.empty()))
    }

    private fun defaultHeaders(node: EthereumNode): Map<String, String>? = node.basicAuth?.let {
        from(
            CollectionConverters.MapHasAsScala(
                mapOf(
                    HttpHeaders.AUTHORIZATION to createBasicAuthHeader(it)
                )
            ).asScala()
        )
    }
    private fun createBasicAuthHeader(auth: String): String {
        val base64Credentials = Base64.getEncoder().encodeToString(auth.toByteArray())
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
                val block = try {
                    ethereum.ethGetBlockByNumber(currentBlockNumber).awaitFirstOrNull()
                } catch (e: Exception) {
                    logger.warn("Can't get block by number for node $rpcUrl, retry...", e)
                    delay(retryBackoffDelay)
                    continue
                }
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
            val nodeInUse = rpcNode.get() ?: return
            try {
                if (nodeInUse.node in externalNodes) {
                    val node = aliveNode(localNodes)
                    logger.info("Found alive node ${node.node}")
                    rpcNode.set(node)
                    return
                }
            } catch (e: RpcCodeException) {
                logger.info("Local nodes are not available")
            }
            try {
                aliveNode(listOf(nodeInUse.node))
            } catch (e: RpcCodeException) {
                logger.info("Current rpc node ${nodeInUse.node} is not alive")
                val node = aliveNode()
                logger.info("Found alive node ${node.node}")
                rpcNode.set(node)
            }
        }

        fun close() {
            shouldRun = false
            interrupt()
        }
    }

    data class EthereumTransport(
        val rpcTransport: WebClientTransport,
        val node: EthereumNode,
    )
}
