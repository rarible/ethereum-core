package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.extractor.TransactionInputExtractor
import com.rarible.ethereum.client.trace.model.HeadTransaction
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import com.rarible.ethereum.client.trace.model.TraceMethod
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

class FallbackTraceCallService(
    private val traceProviderFactory: TransactionTraceProviderFactory,
    private val inputExtractor: TransactionInputExtractor,
    private val enableTraceCallsForMethods: Boolean,
    private val traceMethod: TraceMethod
) : TraceCallService {

    private val traceCallServices = createTraceCallServices()

    override suspend fun findAllRequiredCalls(
        headTransaction: HeadTransaction,
        to: Address,
        vararg ids: Binary
    ): List<SimpleTraceResult> {
        return findTraceCallsWithMethods(
            headTransaction.hash,
            ids,
            traceCallServices,
            enableTraceCallsForMethods
        ) { delegate ->
            delegate.findAllRequiredCalls(headTransaction, to, *ids)
        }
    }

    override suspend fun findAllRequiredCalls(transactionHash: Word, callTypes: Set<String>): List<SimpleTraceResult> {
        return findTraceCall(
            traceCallServices,
            { it.findAllRequiredCalls(transactionHash, callTypes) },
            { emptyList() }
        )
    }

    override suspend fun findAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary> {
        return findTraceCallsWithMethods(txHash, ids, traceCallServices, enableTraceCallsForMethods) { delegate ->
            delegate.findAllRequiredCallInputs(txHash, txInput, to, *ids)
        }
    }

    override suspend fun safeFindAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary> {
        return findTraceCallsWithMethods(
            txHash,
            ids,
            traceCallServices,
            enableTraceCallsForMethods,
            exceptionIfNotFound = false
        ) { delegate ->
            delegate.findAllRequiredCallInputs(txHash, txInput, to, *ids)
        }
    }

    private fun createTraceCallServices(): List<TraceCallService> {
        val create: (TraceMethod) -> TraceCallService = {
            TraceCallServiceImpl(
                traceProvider = traceProviderFactory.traceProvider(it),
                enableTraceCallsForMethods = enableTraceCallsForMethods,
                inputExtractor = inputExtractor,
            )
        }
        val default = listOf(create(traceMethod))
        val fallbacks = TraceMethod.values().filter { it != traceMethod }.map(create)
        return default + fallbacks
    }

    private companion object {

        inline fun <T> findTraceCallsWithMethods(
            tx: Word,
            ids: Array<out Binary>,
            delegates: List<TraceCallService>,
            enableTraceCallsForMethods: Boolean,
            exceptionIfNotFound: Boolean = true,
            call: (TraceCallService) -> List<T>,
        ): List<T> {
            return findTraceCall(delegates, call) {
                if (!enableTraceCallsForMethods) {
                    emptyList()
                } else {
                    if (exceptionIfNotFound) throw TraceNotFoundException("tx trace not found for hash: $tx, ids=${ids.joinToString { it.prefixed() }}") else emptyList()
                }
            }
        }

        inline fun <T> findTraceCall(
            delegates: List<TraceCallService>,
            call: (TraceCallService) -> List<T>,
            onEmptyResult: () -> List<T>
        ): List<T> {
            delegates.forEach { delegate ->
                try {
                    val result = call(delegate)
                    if (result.isNotEmpty()) return result
                } catch (_: TraceNotFoundException) {
                }
            }
            return onEmptyResult()
        }
    }
}
