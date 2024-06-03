package com.rarible.ethereum.client.trace

import com.fasterxml.jackson.databind.JsonNode
import com.rarible.core.test.data.randomString
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import scalether.core.MonoEthereum
import scalether.java.Lists
import java.time.Duration

class CacheableTransactionTraceClientTest {

    private val response: Response<JsonNode> = mockk()
    private val ethereum: MonoEthereum = mockk {
        every { executeRaw(any()) } returns mono { response }
    }

    private val client = CacheableTransactionTraceClient(
        ethereum = ethereum,
        cacheEnabled = true,
        cacheSize = 200,
        cacheTtl = Duration.ofSeconds(1),
    )

    @Test
    fun `cached - ok, fetched and cached`() = runBlocking<Unit> {
        val request = Request(
            1,
            "debug_traceTransaction",
            Lists.toScala(randomString()),
            "2.0"
        )

        client.getTrance(request)
        client.getTrance(request)

        verify(exactly = 1) { ethereum.executeRaw(request) }
    }

    @Test
    fun `cached - ok, cache expired`() = runBlocking<Unit> {

        val request = Request(
            1,
            "debug_traceTransaction",
            Lists.toScala(randomString()),
            "2.0"
        )

        client.getTrance(request)
        delay(1100)
        client.getTrance(request)

        verify(exactly = 2) { ethereum.executeRaw(request) }
    }
}
