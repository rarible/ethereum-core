package com.rarible.ethereum.client.trace

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.rarible.ethereum.client.trace.model.SimpleTraceResult
import com.rarible.ethereum.common.fromHexToBigInteger
import com.rarible.ethereum.common.methodSignatureId
import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Error
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Word
import org.slf4j.LoggerFactory
import scala.Option
import scala.collection.JavaConverters
import scalether.domain.Address
import scalether.java.Lists

class GethTransactionTraceProvider(
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
        ids: Set<Binary>,
    ): List<SimpleTraceResult> {
        logger.info("Get trace for hash: $transactionHash, method: debug_traceTransaction, ids: ${ids.joinToString { it.prefixed() }}")
        return trace(transactionHash).findTraces(to, ids).map { it.toSimpleTraceResult() }
    }

    override suspend fun traceAndFindAllCallsOfType(
        transactionHash: Word,
        callTypes: Set<String>
    ): List<SimpleTraceResult> {
        logger.info("Get trace for hash: $transactionHash, method: debug_traceTransaction, call types: $callTypes")
        return trace(transactionHash).findTraces(callTypes).map { it.toSimpleTraceResult() }
    }

    suspend fun trace(transactionHash: Word): TraceResult {
        val result = traceClient.getTrance(
            Request(
                1, "debug_traceTransaction", Lists.toScala(
                    transactionHash.toString(),
                    JavaConverters.asScala(mapOf("tracer" to "callTracer"))
                ), "2.0"
            )
        )

        if (result.error().isDefined) {
            val error = result.error().get()
            throw RpcCodeException("Unable to get trace hash=$transactionHash", error)
        }

        if (result.result().isEmpty) {
            // Considered as node unavailability, RpcCodeException prevents scanner from skipping blocks
            throw RpcCodeException(
                "Trace result hash=$transactionHash not found",
                Error(-32000, "", Option.apply(""))
            )
        }

        return mapper.treeToValue(result.result().get(), TraceResult::class.java)
    }

    data class TraceResult(
        val from: Address,
        val to: Address?,
        val input: Binary,
        val value: String?,
        val output: Binary?,
        val type: String?,

        val calls: List<TraceResult> = emptyList()
    ) {
        fun findTraces(to: Address, ids: Set<Binary>): List<TraceResult> {
            return calls.flatMap { it.findTracesRaw(to, ids) }
        }

        private fun findTracesRaw(to: Address, ids: Set<Binary>): List<TraceResult> {
            if (this.to == to && input.methodSignatureId() in ids) {
                return listOf(this)
            }
            return calls
                .flatMap { it.findTracesRaw(to, ids) }
        }

        fun findTraces(types: Set<String>): List<TraceResult> {
            val result = mutableListOf<TraceResult>()
            findTracesRaw(types, result)
            return result
        }

        private fun findTracesRaw(types: Set<String>, result: MutableList<TraceResult>) {
            if (type != null && type.lowercase() in types) {
                result.add(this)
            }
            calls.forEach { it.findTracesRaw(types, result) }
        }

        fun toSimpleTraceResult(): SimpleTraceResult {
            return SimpleTraceResult(
                from = from,
                to = to,
                input = input,
                value = value?.fromHexToBigInteger(),
                output = output,
                type = type
            )
        }
    }

    private val logger = LoggerFactory.getLogger(GethTransactionTraceProvider::class.java)
}
