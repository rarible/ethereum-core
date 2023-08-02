package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import scala.reflect.Manifest

class FailoverRpcTransport(private val ethereumTransportProvider: EthereumTransportProvider) : MonoRpcTransport {
    override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> {
        return mono { ethereumTransportProvider.getRpcTransport() }.flatMap { rpcTransport ->
            rpcTransport.send(request, manifest)
                .doOnError {
                    logger.error("Rpc transport encountered an error", it)
                    ethereumTransportProvider.rpcError()
                }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FailoverRpcTransport::class.java)
    }
}
