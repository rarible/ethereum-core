package com.rarible.ethereum.client.trace

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import com.rarible.ethereum.common.fromHexToBigInteger
import com.rarible.ethereum.common.methodSignatureId
import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Word
import org.slf4j.LoggerFactory
import scalether.domain.Address
import scalether.java.Lists

class OpenEthereumTransactionTraceProvider(
    private val traceClient: CacheableTransactionTraceClient
) : TransactionTraceProvider {
    private val mapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    override suspend fun traceAndFindAllCallsTo(
        transactionHash: Word,
        to: Address,
        ids: Set<Binary>
    ): List<SimpleTraceResult> {
        logger.info("Get trace for hash: $transactionHash, method: trace_transaction, ids: ${ids.joinToString { it.prefixed() }}")
        return traces(transactionHash)
            .filter { it.action?.to == to && it.action.input?.methodSignatureId() in ids && it.error != "Reverted" }
            .mapNotNull { convert(it) }
    }

    override suspend fun traceAndFindAllCallsOfType(
        transactionHash: Word,
        callTypes: Set<String>
    ): List<SimpleTraceResult> {
        logger.info("Get trace for hash: $transactionHash, method: trace_transaction, call types: $callTypes")
        return traces(transactionHash)
            .filter { it.type != null && it.type.lowercase() in callTypes }
            .mapNotNull { convert(it) }
    }

    private suspend fun traces(transactionHash: Word): Array<Trace> {
        val request = Request(1, "trace_transaction", Lists.toScala(transactionHash.toString()), "2.0")
        val result = traceClient.getTrance(request)

        if (result.error().isDefined) {
            val error = result.error().get()
            throw RpcCodeException("Unable to get trace hash=$transactionHash", error)
        }

        if (result.result().isEmpty) {
            error("Trace result hash=$transactionHash not found")
        }

        return mapper.treeToValue<Array<Trace>>(result.result().get())
    }

    private fun convert(trace: Trace): SimpleTraceResult? {
        return trace.action?.from?.let {
            SimpleTraceResult(
                from = trace.action.from,
                to = trace.action.to ?: trace.result?.address,
                input = trace.action.input ?: Binary.empty(),
                value = trace.action.value?.fromHexToBigInteger(),
                output = trace.result?.output,
                type = trace.type
            )
        }
    }

    data class Trace(
        val action: Action?,
        val error: String?,
        val result: Result?,
        val type: String?
    ) {
        data class Action(
            val callType: String?,
            val from: Address?,
            val to: Address?,
            val input: Binary?,
            val value: String?
        )

        data class Result(
            val output: Binary?,
            val address: Address?
        )
    }

    private val logger = LoggerFactory.getLogger(OpenEthereumTransactionTraceProvider::class.java)
}
