package com.rarible.ethereum.client

import com.rarible.ethereum.client.failover.FailoverPredicate
import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import scala.reflect.Manifest

class FailoverRpcTransport(
    private val ethereumTransportProvider: EthereumTransportProvider,
    private val failoverPredicate: FailoverPredicate,
) : MonoRpcTransport {
    override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> {
        return mono {
            val rpcTransport = ethereumTransportProvider.getRpcTransport()
            try {
                val response = rpcTransport.send(request, manifest).awaitSingle()
                if (failoverPredicate.needFailover(response)) {
                    return@mono ethereumTransportProvider.getFailoverRpcTransport()?.send(request, manifest)
                        ?.awaitSingle() ?: response
                }
                return@mono response
            } catch (e: Exception) {
                logger.warn("Rpc transport encountered an error", e)
                ethereumTransportProvider.rpcError()
                throw e
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FailoverRpcTransport::class.java)
    }
}
