package com.rarible.ethereum.log.service

import com.rarible.core.logging.LoggingUtils
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.log.LogEventsListener
import com.rarible.ethereum.log.domain.TransactionDto
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import scalether.abi.Signature
import scalether.domain.Address

abstract class AbstractPendingTransactionService(
    private val logEventService: LogEventService,
    private val listener: LogEventsListener
) {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun process(tx: TransactionDto): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            val id = tx.input.slice(0, 4)
            val data = tx.input.slice(4, tx.input.length())
            process(marker, tx.hash, tx.from, tx.nonce, tx.to, id, data)
                .flatMap {
                    logEventService.save(it)
                        .onErrorResume(DuplicateKeyException::class.java) { _ ->
                            logger.warn(marker, "history already created")
                            Mono.just(it)
                        }
                }
                .collectList()
                .flatMapMany { list ->
                    listener.postProcessLogs(list)
                        .thenMany(list.toFlux())
                }
        }
    }

    protected abstract fun process(
        marker: Marker,
        hash: Word,
        from: Address,
        nonce: Long,
        to: Address?,
        id: Binary,
        data: Binary
    ) : Flux<LogEvent>

    protected fun <I> checkTx(id: Binary, data: Binary, signature: Signature<I, *>): I? {
        return if (id == signature.id()) {
            signature.`in`().decode(data, 0).value()
        } else {
            null
        }
    }
}