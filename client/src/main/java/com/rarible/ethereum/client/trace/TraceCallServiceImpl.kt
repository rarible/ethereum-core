package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.extractor.TransactionInputExtractor
import com.rarible.ethereum.client.trace.model.HeadTransaction
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import com.rarible.ethereum.common.methodSignatureId
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.delay
import scalether.domain.Address
import java.math.BigInteger

class TraceCallServiceImpl(
    private val traceProvider: TransactionTraceProvider,
    private val inputExtractor: TransactionInputExtractor,
    private val enableTraceCallsForMethods: Boolean = false
) : TraceCallService {
    // todo get only success traces
    override suspend fun findAllRequiredCalls(
        headTransaction: HeadTransaction,
        to: Address,
        vararg ids: Binary
    ): List<SimpleTraceResult> = findAllRequiredCalls(
        exceptionIfNotFound = true,
        headTransaction = headTransaction,
        to = to,
        ids = ids
    )

    override suspend fun findAllRequiredCalls(
        transactionHash: Word,
        callTypes: Set<String>
    ): List<SimpleTraceResult> {
        return fetchWithRetries {
            traceProvider.traceAndFindAllCallsOfType(
                transactionHash = transactionHash,
                callTypes = callTypes
            )
        }
    }

    override suspend fun findAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary> {
        return findAllRequiredCalls(
            headTransaction = HeadTransaction(
                hash = txHash,
                input = txInput,
                to = Address.ZERO(),
                from = Address.ZERO(),
                value = BigInteger.ZERO
            ),
            to = to,
            ids = ids
        ).map { it.input }
    }

    override suspend fun safeFindAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary> {
        return findAllRequiredCalls(
            exceptionIfNotFound = false,
            headTransaction = HeadTransaction(
                hash = txHash,
                input = txInput,
                to = Address.ZERO(),
                from = Address.ZERO(),
                value = BigInteger.ZERO
            ),
            to = to,
            ids = ids
        ).map { it.input }
    }

    private suspend fun findAllRequiredCalls(
        headTransaction: HeadTransaction,
        to: Address,
        exceptionIfNotFound: Boolean,
        vararg ids: Binary
    ): List<SimpleTraceResult> {
        val set = ids.toSet()
        val txHash = headTransaction.hash
        val txInput = headTransaction.input

        val realInput = inputExtractor.extract(txInput)
        if (realInput.methodSignatureId() in set) {
            return listOf(
                SimpleTraceResult(
                    from = headTransaction.from,
                    to = headTransaction.to,
                    value = headTransaction.value,
                    input = realInput,
                    output = null,
                    type = null,
                )
            )
        } else if (!enableTraceCallsForMethods) {
            return emptyList()
        } else {
            val result = fetchWithRetries { traceProvider.traceAndFindAllCallsTo(txHash, to, set) }
            return if (exceptionIfNotFound && result.isEmpty())
                throw TraceNotFoundException("tx trace not found for hash: $txHash, ids=${ids.joinToString { it.prefixed() }}") else result
        }
    }

    private suspend fun fetchWithRetries(
        call: suspend () -> List<SimpleTraceResult>
    ): List<SimpleTraceResult> {
        // TODO ideally is to put this numbers to config
        var attempts = 0
        do {
            val tracesFound = call()
            if (tracesFound.isNotEmpty()) {
                return tracesFound
            }
            delay(200)
        } while (attempts++ < 5)
        return emptyList()
    }
}
