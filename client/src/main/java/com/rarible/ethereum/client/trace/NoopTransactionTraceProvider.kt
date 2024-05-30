package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

class NoopTransactionTraceProvider : TransactionTraceProvider {

    override suspend fun traceAndFindAllCallsTo(
        transactionHash: Word,
        to: Address,
        ids: Set<Binary>
    ): List<SimpleTraceResult> {
        return emptyList()
    }

    override suspend fun traceAndFindAllCallsOfType(
        transactionHash: Word,
        callTypes: Set<String>
    ): List<SimpleTraceResult> {
        return emptyList()
    }
}
