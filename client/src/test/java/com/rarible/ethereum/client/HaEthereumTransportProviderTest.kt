package com.rarible.ethereum.client

import com.rarible.ethereum.client.failover.NoopFailoverPredicate
import com.rarible.ethereum.client.failover.SimplePredicate
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.StringBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClientResponseException
import scala.jdk.javaapi.CollectionConverters
import scala.reflect.Manifest
import scalether.abi.Uint32Type
import scalether.core.MonoEthereum
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
        logger.info(
            "rpcInternalServerPort = ${rpcInternalServer.localPort}, " +
                "rpcInternalServer2Port=$rpcInternalServer2Port, " +
                "rpcExternalServerPort=${rpcExternalServer.localPort}"
        )

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
            readWriteTimeoutMs = 1000,
            maxBlockDelay = Duration.ofSeconds(100),
            allowTransactionsWithoutHash = false,
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

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 415])
    fun `should not retry for 4xx errors`(statusCode: Int) = runBlocking<Unit> {
        // Given
        val mockServer = MockWebServer()
        mockServer.start()
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
        )
        mockServer.enqueue(MockResponse().setResponseCode(statusCode))
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "response"}""")
        )

        // And
        val transport = rpcTransport(mockServer)

        // Then
        assertThrows<WebClientResponseException> {
            val request = Request(1L, "GET", CollectionConverters.asScala(emptyList<Any>()).toList(), "")
            transport.send(request, Manifest.Any()).awaitSingle()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 504])
    fun `should retry for server errors`(statusCode: Int) = runBlocking<Unit> {
        // Given
        val mockServer = MockWebServer()
        mockServer.start()
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockNumberResponse(1))
        )
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(getBlockResponse())
        )
        mockServer.enqueue(MockResponse().setResponseCode(statusCode))
        mockServer.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"jsonrpc": "2.0","id": 2,"result": "resp"}""")
        )

        // And
        val transport = rpcTransport(mockServer)

        // When
        val request = Request(1L, "GET", CollectionConverters.asScala(emptyList<Any>()).toList(), "")
        val response = transport.send(request, Manifest.Any()).awaitSingle()

        // Then
        assertThat(response.result().get()).isEqualTo("resp")
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
            allowTransactionsWithoutHash = false,
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
            allowTransactionsWithoutHash = false,
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
            allowTransactionsWithoutHash = false,
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
            allowTransactionsWithoutHash = false,
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
            allowTransactionsWithoutHash = false,
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

    @Test
    fun `hedera invalid hash`() = runBlocking<Unit> {
        val rpcInternalServer = ClientAndServer.startClientAndServer()
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
                .withBody(StringBody.subString("eth_blockNumber"))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockNumberResponse(1))
            )

        rpcInternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString(""""method":"eth_getBlockByNumber","params":["0x1",false],"""))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(getBlockResponse())
            )
        rpcInternalServer.`when`(
            HttpRequest.request()
                .withBody(StringBody.subString(""""method":"eth_getBlockByNumber","params":["0x4bde21",true],"""))
        )
            .respond(
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """{
  "jsonrpc": "2.0",
  "id": 8511,
  "result": {
    "timestamp": "0x665f24da",
    "difficulty": "0x0",
    "extraData": "0x",
    "gasLimit": "0x1c9c380",
    "baseFeePerGas": "0x5625b7f400",
    "gasUsed": "0x0",
    "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "miner": "0x0000000000000000000000000000000000000000",
    "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
    "nonce": "0x0000000000000000",
    "receiptsRoot": "0xcb2ce1552fce9c5895d12961dcdf68bfbda94009517ee72a5dfa11cba1609c1e",
    "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
    "size": "0x656",
    "stateRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
    "totalDifficulty": "0x0",
    "transactions": [
      {
        "blockHash": "0xb01fbda672c38eb83b4e729e29ad03b84dcd397a391e1d0802a144d73dddc771",
        "blockNumber": "0x4bde21",
        "chainId": "0x128",
        "from": "0x208b15dab9903be8d34336d0b7f930e5f0a76ec5",
        "gas": "0x0",
        "gasPrice": "0x0",
        "hash": "0x",
        "input": "0x",
        "nonce": "0xdd4",
        "r": "0xbf2556a8f536c0bab90c8bc209aaa7b462a5022550675995f1cd220408786185",
        "s": "0x79096aacdc8a6cbe19711a577d01abfdc8fd743a3641e2868bef03071e872da",
        "transactionIndex": "0x3",
        "type": "0x2",
        "v": "0x1",
        "value": "0x0",
        "yParity": "0x1",
        "accessList": [],
        "maxPriorityFeePerGas": "0x0",
        "maxFeePerGas": "0x6b"
      }
    ],
    "transactionsRoot": "0xb01fbda672c38eb83b4e729e29ad03b84dcd397a391e1d0802a144d73dddc771",
    "uncles": [],
    "withdrawals": [],
    "withdrawalsRoot": "0x0000000000000000000000000000000000000000000000000000000000000000",
    "number": "0x4bde21",
    "hash": "0xb01fbda672c38eb83b4e729e29ad03b84dcd397a391e1d0802a144d73dddc771",
    "parentHash": "0x043970b7830a7a0a992384cfda3dddd4d43178f32c31287b359b18400764d1a6"
  }
}"""
                    )
            )
        val provider = HaEthereumTransportProvider(
            monitoringThreadInterval = Duration.ofSeconds(100),
            localNodes = listOf(
                EthereumNode(
                    httpUrl = "http://127.0.0.1:${rpcInternalServer.localPort}",
                )
            ),
            externalNodes = emptyList(),
            maxFrameSize = 1024 * 1024,
            retryMaxAttempts = 5,
            retryBackoffDelay = 100,
            requestTimeoutMs = 10000,
            readWriteTimeoutMs = 10000,
            allowTransactionsWithoutHash = true,
            maxBlockDelay = Duration.ofDays(100000)
        )

        val transport = provider.getRpcTransport()
        val result = MonoEthereum(transport).ethGetFullBlockByNumber(4972065.toBigInteger()).awaitSingle()
        assertThat(result.transactions().size()).isGreaterThan(0)
        assertThat(
            result.transactions().last().hash()
        ).isEqualTo(Word.apply("0x0000000000000000000000000000000000000000000000000000000000000000"))
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

    private fun rpcTransport(rpcInternalServer: MockWebServer): FailoverRpcTransport {
        return FailoverRpcTransport(
            ethereumTransportProvider = HaEthereumTransportProvider(
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
                allowTransactionsWithoutHash = false,
            ),
            failoverPredicate = NoopFailoverPredicate(),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HaEthereumTransportProviderTest::class.java)
    }
}
