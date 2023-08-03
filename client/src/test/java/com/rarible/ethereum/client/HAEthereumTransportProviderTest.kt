package com.rarible.ethereum.client

import com.rarible.ethereum.client.failover.NoopFailoverPredicate
import com.rarible.ethereum.client.failover.SimplePredicate
import io.daonomic.rpc.domain.Request
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.Option
import scala.collection.Map
import scala.jdk.javaapi.CollectionConverters
import scala.reflect.Manifest
import java.net.ServerSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class HAEthereumTransportProviderTest {

    @Test
    fun `fallback to external and then reconnect to internal`() = runBlocking<Unit> {
        val internalServer = MockWebServer()
        internalServer.start()
        val internalServer2 = MockWebServer()
        val internalServer2Port = ServerSocket(0).use { it.localPort }
        val externalServer = MockWebServer()
        externalServer.start()

        internalServer.enqueue(
            MockResponse().withWebSocketUpgrade(
                MockWebsocketListener(
                    response = "request1"
                )
            )
        )
        internalServer2.enqueue(
            MockResponse().withWebSocketUpgrade(
                MockWebsocketListener(
                    response = "request3"
                )
            )
        )
        val externalServerListener = MockWebsocketListener(
            response = "request2"
        )
        externalServer.enqueue(MockResponse().withWebSocketUpgrade(externalServerListener))
        val rpcInternalServer = MockWebServer()
        rpcInternalServer.start()
        val rpcInternalServer2 = MockWebServer()
        val rpcInternalServer2Port = ServerSocket(0).use { it.localPort }
        val rpcExternalServer = MockWebServer()
        rpcExternalServer.start()
        logger.info(
            """Internal server 1: rpcPort=${rpcInternalServer.port}, wsPort=${internalServer.port}
            |Internal server 2: rpcPort=$rpcInternalServer2Port, wsPort=$internalServer2Port
            |External server: rpcPort=${rpcExternalServer.port}, wsPort=${externalServer.port}
        """.trimMargin()
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response1"}""")
        )
        rpcInternalServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcInternalServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcInternalServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response3"}""")
        )
        rpcExternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcExternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
        )
        val provider = HAEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcInternalServer.port}",
                    websocketUrl = "ws://127.0.0.1:${internalServer.port}"
                ),
                EthereumNode(
                    httpUrl = "http://127.0.0.1:$rpcInternalServer2Port",
                    websocketUrl = "ws://127.0.0.1:$internalServer2Port"
                )
            ),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcExternalServer.port}",
                    websocketUrl = "ws://127.0.0.1:${externalServer.port}"
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
        )

        // Should receive event from server1
        val receivedEvents = LinkedBlockingQueue<Any>()
        val subscription = FailoverPubSubTransport(provider).subscribe("test", Option.empty(), Manifest.Object())
            .doOnNext {
                receivedEvents.add((it as Map<String, String>).get("data").get())
            }
            .then(Mono.error<Void>(IllegalStateException("disconnected")))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(300))
                    .maxBackoff(Duration.ofMillis(2000))
            )
            .subscribe()
        assertThat(receivedEvents.poll(5, TimeUnit.SECONDS)).isEqualTo("request1")
        rpcInternalServer.shutdown()
        internalServer.shutdown()
        // Should receive event from external server after reconnect
        assertThat(receivedEvents.poll(5, TimeUnit.SECONDS)).isEqualTo("request2")

        // Should get response from external server
        val response = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = NoopFailoverPredicate(),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                ""
            ),
            Manifest.Any(),
        ).awaitSingle()
        assertThat(response.result().get()).isEqualTo("response2")

        // Starting internal server 2
        internalServer2.start(internalServer2Port)
        rpcInternalServer2.start(rpcInternalServer2Port)

        // Should receive event from internal server 2 after reconnect
        assertThat(receivedEvents.poll(5, TimeUnit.SECONDS)).isEqualTo("request3")

        // Should get response from internal server 2
        val response2 = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = NoopFailoverPredicate(),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                ""
            ),
            Manifest.Any(),
        ).awaitSingle()
        assertThat(response2.result().get()).isEqualTo("response3")
    }

    @Test
    fun `rpc failover to external node`() = runBlocking<Unit> {
        val internalServer = MockWebServer()
        internalServer.start()
        val externalServer = MockWebServer()
        externalServer.start()

        val provider = HAEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${internalServer.port}",
                    websocketUrl = "ws://127.0.0.1:${internalServer.port}"
                ),
            ),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer.port}",
                    websocketUrl = "ws://127.0.0.1:${externalServer.port}"
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
        )

        internalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        internalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response1", "error": { "message": "test", "code": -32000 }}""")
        )
        externalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
        )

        val response = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = SimplePredicate(code = -32000, errorMessagePrefix = "test"),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                ""
            ),
            Manifest.Any(),
        ).awaitSingle()

        assertThat(response.result().get()).isEqualTo("response2")
    }

    @Test
    fun `rpc no failover already on external node`() = runBlocking<Unit> {
        val externalServer1 = MockWebServer()
        externalServer1.start()
        val externalServer2 = MockWebServer()
        externalServer2.start()

        val provider = HAEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = emptyList(),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer1.port}",
                    websocketUrl = "ws://127.0.0.1:${externalServer1.port}"
                ),
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer2.port}",
                    websocketUrl = "ws://127.0.0.1:${externalServer2.port}"
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
        )

        externalServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        externalServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response1", "error": { "message": "test", "code": -32000 }}""")
        )
        externalServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
        )

        val response = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = SimplePredicate(code = -32000, errorMessagePrefix = "test"),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                ""
            ),
            Manifest.Any(),
        ).awaitSingle()

        assertThat(response.result().get()).isEqualTo("response1")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HAEthereumTransportProviderTest::class.java)
    }
}

class MockWebsocketListener(private val response: String) : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        val subscriptionId = UUID.randomUUID().toString()
        webSocket.send(
            """{
                    "jsonrpc": "2.0",
                    "id": "1",
                    "result": "$subscriptionId"
                }""".trimMargin()
        )

        webSocket.send(
            """{
                    "jsonrpc": "2.0",
                    "method": "eth_subscription",
                    "params": {
                        "result": {
                            "data": "$response"
                        }
                    },
                    "subscription": "$subscriptionId"
                }"""
        )
    }
}
