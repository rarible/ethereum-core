package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.model.HeadTransaction
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.domain.Address

interface TraceCallService {
    suspend fun findAllRequiredCalls(
        headTransaction: HeadTransaction,
        to: Address,
        vararg ids: Binary
    ): List<SimpleTraceResult>

    suspend fun findAllRequiredCalls(
        transactionHash: Word,
        callTypes: Set<String>,
    ): List<SimpleTraceResult>

    suspend fun findAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary>

    suspend fun safeFindAllRequiredCallInputs(
        txHash: Word,
        txInput: Binary,
        to: Address,
        vararg ids: Binary
    ): List<Binary>
}
