package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

interface TransactionTraceProvider {
    /**
     * Finds all calls to specific contract with specific identifier
     */
    suspend fun traceAndFindAllCallsTo(transactionHash: Word, to: Address, ids: Set<Binary>): List<SimpleTraceResult>

    suspend fun traceAndFindAllCallsOfType(transactionHash: Word, callTypes: Set<String>): List<SimpleTraceResult>
}
