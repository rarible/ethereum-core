package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.model.TraceMethod
import scalether.core.MonoEthereum
import java.time.Duration

class TransactionTraceProviderFactory(
    private val ethereum: MonoEthereum,
    private val cacheEnabled: Boolean = true,
    private val cacheSize: Long = 200,
    private val cacheTtl: Duration = Duration.ofSeconds(30)
) {
    fun traceProvider(method: TraceMethod): TransactionTraceProvider {
        val client = CacheableTransactionTraceClient(
            ethereum = ethereum,
            cacheEnabled = cacheEnabled,
            cacheSize = cacheSize,
            cacheTtl = cacheTtl
        )
        return when (method) {
            TraceMethod.TRACE_TRANSACTION -> {
                OpenEthereumTransactionTraceProvider(client)
            }

            TraceMethod.DEBUG_TRACE_TRANSACTION -> {
                GethTransactionTraceProvider(client)
            }
        }
    }
}
