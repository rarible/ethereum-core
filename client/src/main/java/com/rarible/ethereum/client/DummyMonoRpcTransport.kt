package com.rarible.ethereum.client

import io.daonomic.rpc.MonoRpcTransport
import io.daonomic.rpc.domain.Request
import reactor.core.publisher.Mono
import scala.reflect.Manifest

class DummyMonoRpcTransport : MonoRpcTransport {
    override fun <T : Any?> send(request: Request?, manifest: Manifest<T>): Mono<T> {
        return Mono.empty()
    }
}
