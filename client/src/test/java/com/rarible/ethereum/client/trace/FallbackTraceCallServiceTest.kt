package com.rarible.ethereum.client.trace

import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomBigInt
import com.rarible.core.test.data.randomBinary
import com.rarible.core.test.data.randomWord
import com.rarible.ethereum.client.trace.extractor.TransactionInputExtractor
import com.rarible.ethereum.client.trace.model.HeadTransaction
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import com.rarible.ethereum.client.trace.model.TraceMethod
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.stream.Stream

internal class FallbackTraceCallServiceTest {
    private val defaultProvider = mockk<TransactionTraceProvider>()
    private val otherProvider = mockk<TransactionTraceProvider>()
    private val traceProviderFactory = mockk<TransactionTraceProviderFactory> {
        every { traceProvider(TraceMethod.TRACE_TRANSACTION) } returns defaultProvider
        every { traceProvider(TraceMethod.DEBUG_TRACE_TRANSACTION) } returns otherProvider
    }
    private val inputExtractor = mockk<TransactionInputExtractor>() {
        every { extract(any()) } returnsArgument 0
    }
    private val fallbackTraceCallService = FallbackTraceCallService(
        traceProviderFactory = traceProviderFactory,
        inputExtractor = inputExtractor,
        enableTraceCallsForMethods = true,
        traceMethod = TraceMethod.TRACE_TRANSACTION
    )

    @Test
    fun `should use default TraceCallService from config`() = runBlocking<Unit> {
        val simpleTrace = randomSimpleTrace()
        val headTransaction = randomHeadTransaction()
        coEvery { defaultProvider.traceAndFindAllCallsTo(eq(headTransaction.hash), any(), any()) } returns listOf(
            simpleTrace
        )

        fallbackTraceCallService.findAllRequiredCalls(headTransaction, randomAddress(), randomBinary())

        coVerify(exactly = 1) { defaultProvider.traceAndFindAllCallsTo(any(), any(), any()) }
        coVerify(exactly = 0) { otherProvider.traceAndFindAllCallsTo(any(), any(), any()) }
    }

    @Test
    fun `should use other TraceCallService`() = runBlocking<Unit> {
        val headTransaction = randomHeadTransaction()
        val simpleTrace = randomSimpleTrace()
        coEvery { defaultProvider.traceAndFindAllCallsTo(eq(headTransaction.hash), any(), any()) } returns emptyList()
        coEvery { otherProvider.traceAndFindAllCallsTo(eq(headTransaction.hash), any(), any()) } returns listOf(
            simpleTrace
        )

        fallbackTraceCallService.findAllRequiredCalls(headTransaction, randomAddress(), randomBinary())

        coVerify(atLeast = 1) { defaultProvider.traceAndFindAllCallsTo(any(), any(), any()) }
        coVerify(exactly = 1) { otherProvider.traceAndFindAllCallsTo(any(), any(), any()) }
    }

    @ParameterizedTest
    @MethodSource("fallbackErrors")
    fun `should fallback for specific exceptions`(ex: Exception) = runBlocking {
        val txn = randomHeadTransaction()
        coEvery { defaultProvider.traceAndFindAllCallsTo(eq(txn.hash), any(), any()) } throws ex
        coEvery { otherProvider.traceAndFindAllCallsTo(eq(txn.hash), any(), any()) } returns listOf(
            randomSimpleTrace()
        )

        fallbackTraceCallService.findAllRequiredCalls(txn, randomAddress(), randomBinary())

        coVerify(exactly = 1) { defaultProvider.traceAndFindAllCallsTo(any(), any(), any()) }
        coVerify(exactly = 1) { otherProvider.traceAndFindAllCallsTo(any(), any(), any()) }
    }

    @ParameterizedTest
    @MethodSource("nonFallbackErrors")
    fun `should not fallback for unknown exceptions`(ex: Exception) = runBlocking {
        val txn = randomHeadTransaction()
        coEvery { defaultProvider.traceAndFindAllCallsTo(eq(txn.hash), any(), any()) } throws ex
        coEvery { otherProvider.traceAndFindAllCallsTo(eq(txn.hash), any(), any()) } returns listOf(
            randomSimpleTrace()
        )

        assertThrows<Exception> {
            fallbackTraceCallService.findAllRequiredCalls(txn, randomAddress(), randomBinary())
        }

        coVerify(exactly = 1) { defaultProvider.traceAndFindAllCallsTo(any(), any(), any()) }
        coVerify(exactly = 0) { otherProvider.traceAndFindAllCallsTo(any(), any(), any()) }
    }

    private fun randomSimpleTrace(): SimpleTraceResult {
        return SimpleTraceResult(
            from = randomAddress(),
            to = randomAddress(),
            input = Binary.empty(),
            value = randomBigInt(),
            output = Binary.empty(),
            type = "call"
        )
    }

    private fun randomHeadTransaction(): HeadTransaction {
        return HeadTransaction(
            hash = Word.apply(randomWord()),
            input = randomBinary(),
            from = randomAddress(),
            to = randomAddress(),
            value = randomBigInt()
        )
    }

    companion object {
        @JvmStatic
        fun fallbackErrors(): Stream<Exception> = Stream.of(
            TraceNotFoundException("error"),
            WebClientResponseException.create(400, "not found", HttpHeaders(), byteArrayOf(), null, null),
        )

        @JvmStatic
        fun nonFallbackErrors(): Stream<Exception> = Stream.of(
            RuntimeException("unknown"),
            WebClientResponseException.create(500, "not found", HttpHeaders(), byteArrayOf(), null, null),
            WebClientResponseException.create(401, "unauthorized", HttpHeaders(), byteArrayOf(), null, null),
        )
    }
}
