package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.domain.Request
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.Option
import scala.collection.Map
import scala.jdk.javaapi.CollectionConverters
import scala.reflect.Manifest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class EthereumTransportProviderTest {

    @Test
    fun reconnect() = runBlocking<Unit> {
        val wsServer1 = MockWebServer()
        wsServer1.start()
        val wsServer2 = MockWebServer()
        wsServer2.start()
        wsServer1.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
            }

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
                            "data": "request1"
                        }
                    },
                    "subscription": "$subscriptionId"
                }"""
                )
            }
        }))
        wsServer2.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
            }

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
                            "data": "request2"
                        }
                    },
                    "subscription": "$subscriptionId"
                }"""
                )
            }
        }))
        val rpcServer1 = MockWebServer()
        rpcServer1.start()
        val rpcServer2 = MockWebServer()
        rpcServer2.start()
        rpcServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response1"}""")
        )
        rpcServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": true}""")
        )
        rpcServer2.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
        )
        val provider = EthereumTransportProvider(
            EthereumProperties(
                httpUrl = null,
                websocketUrl = null,
                nodes = listOf(
                    NodeProperty(
                        httpUrl = "http://127.0.0.1:${rpcServer1.port}",
                        websocketUrl = "ws://127.0.0.1:${wsServer1.port}"
                    ),
                    NodeProperty(
                        httpUrl = "http://127.0.0.1:${rpcServer2.port}",
                        websocketUrl = "ws://127.0.0.1:${wsServer2.port}"
                    )
                )
            )
        )

        // Should receive event from server1
        val receivedEvents = LinkedBlockingQueue<Any>()
        FailoverPubSubTransport(provider).subscribe("test", Option.empty(), Manifest.Object())
            .doOnNext {
                receivedEvents.add((it as Map<String, String>).get("data").get())
            }
            .then(Mono.error<Void>(IllegalStateException("disconnected")))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(300))
                    .maxBackoff(Duration.ofMillis(2000))
            ).subscribe()
        assertThat(receivedEvents.poll(5, TimeUnit.SECONDS)).isEqualTo("request1")
        rpcServer1.shutdown()
        wsServer1.shutdown()
        // Should receive event from server2 after reconnect
        assertThat(receivedEvents.poll(5, TimeUnit.SECONDS)).isEqualTo("request2")

        // Should get response from server2
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
    }
}
