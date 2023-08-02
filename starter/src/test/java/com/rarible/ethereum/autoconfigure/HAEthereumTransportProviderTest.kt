package com.rarible.ethereum.autoconfigure

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
            EthereumProperties(
                httpUrl = null,
                websocketUrl = null,
                monitoringThreadInterval = Duration.ofMillis(100),
                nodes = listOf(
                    NodeProperty(
                        httpUrl = "http://127.0.0.1:${rpcInternalServer.port}",
                        websocketUrl = "ws://127.0.0.1:${internalServer.port}"
                    ),
                    NodeProperty(
                        httpUrl = "http://127.0.0.1:$rpcInternalServer2Port",
                        websocketUrl = "ws://127.0.0.1:$internalServer2Port"
                    )
                ),
                externalNodes = listOf(
                    NodeProperty(
                        httpUrl = "http://127.0.0.1:${rpcExternalServer.port}",
                        websocketUrl = "ws://127.0.0.1:${externalServer.port}"
                    )
                )
            )
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
        val response = FailoverRpcTransport(provider).send(
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
        val response2 = FailoverRpcTransport(provider).send(
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

    companion object {
        private val logger = LoggerFactory.getLogger(MockWebsocketListener::class.java)
    }
}
