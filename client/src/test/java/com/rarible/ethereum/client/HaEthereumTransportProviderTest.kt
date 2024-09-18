package com.rarible.ethereum.client

import com.rarible.ethereum.client.failover.NoopFailoverPredicate
import com.rarible.ethereum.client.failover.SimplePredicate
import io.daonomic.rpc.domain.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.StringBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import scala.jdk.javaapi.CollectionConverters
import scala.reflect.Manifest
import scalether.abi.Uint32Type
import java.math.BigInteger
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

internal class HaEthereumTransportProviderTest {

    @Test
    fun `fallback to external and then reconnect to internal`() = runBlocking<Unit> {
        val rpcInternalServer = ClientAndServer.startClientAndServer()
        val rpcInternalServer2Port = ServerSocket(0).use { it.localPort }
        val rpcExternalServer = ClientAndServer.startClientAndServer()
        logger.info("rpcInternalServerPort = ${rpcInternalServer.localPort}, " +
            "rpcInternalServer2Port=${rpcInternalServer2Port}, " +
            "rpcExternalServerPort=${rpcExternalServer.localPort}")

        rpcInternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_blockNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockNumberResponse(1))
            )

        rpcInternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_getBlockByNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockResponse())
            )

        rpcInternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("test"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody("""{"jsonrpc": "2.0","id": 2,"result": "response1"}""")
            )

        rpcExternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_blockNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockNumberResponse(1))
            )

        rpcExternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_getBlockByNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockResponse())
            )

        rpcExternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("test"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
            )

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcInternalServer.localPort}",
                ),
                EthereumNode(
                    httpUrl = "http://127.0.0.1:$rpcInternalServer2Port",
                )
            ),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcExternalServer.localPort}",
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 1,
            retryBackoffDelay = 0,
            requestTimeoutMs = 0,
            readWriteTimeoutMs = 100,
            maxBlockDelay = Duration.ofSeconds(100),
        )

        val response1 = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = NoopFailoverPredicate(),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                "test"
            ),
            Manifest.Any(),
        ).awaitSingle()
        assertThat(response1.result().get()).isEqualTo("response1")
        // Should receive event from server1
        rpcInternalServer.close()

        // Should get response from external server
        val response = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = NoopFailoverPredicate(),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                "test"
            ),
            Manifest.Any(),
        )
            .retry(3)
            .awaitSingle()
        assertThat(response.result().get()).isEqualTo("response2")

        // Starting internal server 2
        val rpcInternalServer2 = ClientAndServer.startClientAndServer(rpcInternalServer2Port)

        rpcInternalServer2.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_blockNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockNumberResponse(1))
            )

        rpcInternalServer2.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("eth_getBlockByNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockResponse())
            )

        rpcInternalServer2.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString("test"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody("""{"jsonrpc": "2.0","id": 2,"result": "response3"}""")
            )

        delay(5000)

        // Should get response from internal server 2
        val response2 = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = NoopFailoverPredicate(),
        ).send(
            Request(
                1L,
                "GET",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                "test"
            ),
            Manifest.Any(),
        ).awaitSingle()
        assertThat(response2.result().get()).isEqualTo("response3")
    }

    @Test
    fun `retry if no block return`() = runBlocking<Unit> {
        val rpcInternalServer = MockWebServer()
        rpcInternalServer.start()

        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getNullResponse())
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response1"}""")
        )

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcInternalServer.port}",
                ),
            ),
            externalNodes = emptyList(),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 0,
            requestTimeoutMs = 0,
            readWriteTimeoutMs = 10000,
            maxBlockDelay = Duration.ofSeconds(10),
        )

        val response1 = FailoverRpcTransport(
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
        assertThat(response1.result().get()).isEqualTo("response1")
    }

    @Test
    fun `fallback to external if block delay`() = runBlocking<Unit> {
        val rpcInternalServer = MockWebServer()
        rpcInternalServer.start()
        val rpcExternalServer = MockWebServer()
        rpcExternalServer.start()

        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        rpcInternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse(Instant.MIN.epochSecond))
        )
        rpcExternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(4))
        )
        rpcExternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
        )
        rpcExternalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response2"}""")
        )

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcInternalServer.port}",
                ),
            ),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcExternalServer.port}",
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 0,
            requestTimeoutMs = 0,
            readWriteTimeoutMs = 10000,
            maxBlockDelay = Duration.ofSeconds(10),
        )

        val response1 = FailoverRpcTransport(
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
        assertThat(response1.result().get()).isEqualTo("response2")
    }

    @Test
    fun `rpc failover to external node`() = runBlocking<Unit> {
        val internalServer = MockWebServer()
        internalServer.start()
        val externalServer = MockWebServer()
        externalServer.start()

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${internalServer.port}",
                ),
            ),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer.port}",
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
            maxBlockDelay = Duration.ofSeconds(10),
        )

        internalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        internalServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
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

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = emptyList(),
            externalNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer1.port}",
                ),
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${externalServer2.port}",
                )
            ),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
            maxBlockDelay = Duration.ofSeconds(10),
        )

        externalServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        externalServer1.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
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

    @Test
    fun `rpc basic auth`() = runBlocking<Unit> {
        val nodeServer = MockWebServer()
        nodeServer.start()

        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofMillis(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://user:password@127.0.0.1:${nodeServer.port}",
                )
            ),
            externalNodes = emptyList(),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
            maxBlockDelay = Duration.ofSeconds(10),
        )
        nodeServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        nodeServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
        )
        nodeServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 1,"result": "0xa4b1"}""")
        )

        val response = FailoverRpcTransport(
            ethereumTransportProvider = provider,
            failoverPredicate = SimplePredicate(code = -32000, errorMessagePrefix = "test"),
        ).send(
            Request(
                1L,
                "eth_chainId",
                CollectionConverters.asScala(emptyList<Any>()).toList(),
                "2.0"
            ),
            Manifest.Any(),
        ).awaitSingle()

        assertThat(response.result().get()).isEqualTo("0xa4b1")

        repeat(3) {
            val recordedRequest = nodeServer.takeRequest()
            val expectedCredentials = Credentials.basic("user", "password")
            val actualCredentials = recordedRequest.getHeader("Authorization")
            assertThat(actualCredentials).isEqualTo(expectedCredentials)
        }
    }

    private fun getBlockNumberResponse(number: Long): String {
        return """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "${Uint32Type.encode(BigInteger.valueOf(number)).slice(28, 32).prefixed()}"
            }
        """.trimIndent()
    }

    private fun getNullResponse(): String {
        return """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": null
            }
        """.trimIndent()
    }

    private fun getBlockResponse(timestamp: Long = Instant.now().epochSecond): String {
        return """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": {
                    "difficulty": "0x0",
                    "extraData": "0x",
                    "gasLimit": "0x112a8800",
                    "gasUsed": "0x0",
                    "hash": "0xe0594250efac73640aeff78ec40aaaaa87f91edb54e5af926ee71a32ef32da34",
                    "l1BlockNumber": "0x0",
                    "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                    "miner": "0x0000000000000000000000000000000000000000",
                    "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
                    "nonce": "0x0000000000000000",
                    "number": "0x1",
                    "parentHash": "0x7ee576b35482195fc49205cec9af72ce14f003b9ae69f6ba0faef4514be8b442",
                    "receiptsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                    "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
                    "size": "0x1fd",
                    "stateRoot": "0x0000000000000000000000000000000000000000000000000000000000000000",
                    "timestamp": "${Uint32Type.encode(BigInteger.valueOf(timestamp)).slice(28, 32).prefixed()}",
                    "totalDifficulty": "0x0",
                    "transactions": [],
                    "transactionsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                    "uncles": []
                }
            }
        """.trimIndent()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HaEthereumTransportProviderTest::class.java)
    }
}
