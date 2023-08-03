package com.rarible.ethereum.client

import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import scala.Option
import scala.reflect.Manifest
import scalether.core.PubSubTransport
import java.util.concurrent.atomic.AtomicReference

class FailoverPubSubTransport(private val ethereumTransportProvider: EthereumTransportProvider) : PubSubTransport {
    override fun <T : Any?> subscribe(name: String?, param: Option<Any>?, manifest: Manifest<T>?): Flux<T> {
        return Flux.create { sink ->
            val disposable = AtomicReference(subscribe(name, param, manifest, sink))
            val reconnectCallback = {
                logger.info("Resubscribing")
                disposable.get().dispose()
                disposable.set(subscribe(name, param, manifest, sink))
            }
            val terminateCallback = {
                ethereumTransportProvider.unregisterWebsocketSubscription(reconnectCallback)
                disposable.get().dispose()
            }
            sink.onCancel(terminateCallback)
            sink.onDispose(terminateCallback)
            ethereumTransportProvider.registerWebsocketSubscription(reconnectCallback)
        }
    }

    private fun <T : Any?> subscribe(
        name: String?,
        param: Option<Any>?,
        manifest: Manifest<T>?,
        sink: FluxSink<T>
    ) = mono { ethereumTransportProvider.getWebsocketTransport() }.flatMapMany { delegate ->
        delegate.subscribe(name, param, manifest)
            .doOnCancel {
                logger.error("PubSub transport cancelled")
                ethereumTransportProvider.websocketDisconnected()
            }
            .doOnTerminate {
                logger.error("PubSub transport disconnected")
                ethereumTransportProvider.websocketDisconnected()
            }
            .doOnSubscribe {
                logger.info("Subscribed on websocket events")
            }
    }.subscribe(
        { sink.next(it) },
        { sink.error(it) },
        { sink.complete() },
    )

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FailoverPubSubTransport::class.java)
    }
}
