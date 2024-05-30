package com.rarible.ethereum.client.trace

import com.rarible.ethereum.client.trace.model.TraceMethod
import scalether.core.MonoEthereum

class TransactionTraceProviderFactory(private val ethereum: MonoEthereum) {
    fun traceProvider(method: TraceMethod): TransactionTraceProvider {
        return when (method) {
            TraceMethod.TRACE_TRANSACTION -> {
                OpenEthereumTransactionTraceProvider(ethereum)
            }

            TraceMethod.DEBUG_TRACE_TRANSACTION -> {
                GethTransactionTraceProvider(ethereum)
            }
        }
    }
}
