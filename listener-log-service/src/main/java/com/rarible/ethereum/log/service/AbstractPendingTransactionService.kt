package com.rarible.ethereum.log.service

import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.log.LogEventsListener
import com.rarible.ethereum.log.domain.TransactionDto
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import scalether.abi.Signature
import scalether.domain.Address

abstract class AbstractPendingTransactionService(
    private val logEventService: LogEventService,
    private val listener: LogEventsListener
) {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(tx: TransactionDto): List<LogEvent> {
        val id = tx.input.slice(0, 4)
        val data = tx.input.slice(4, tx.input.length())
        val logs = process(tx.hash, tx.from, tx.to, id, data)
            .map { saveOrReturn(it) }
            .toList()

        listener.postProcessLogs(logs).awaitFirstOrNull()
        return logs
    }

    private suspend fun saveOrReturn(log: LogEvent): LogEvent {
        return try {
            logEventService.save(log).awaitFirst()
        } catch (e: DuplicateKeyException) {
            logger.warn("history already created")
            log
        }
    }

    protected abstract suspend fun process(
        hash: Word,
        from: Address,
        to: Address?,
        id: Binary,
        data: Binary
    ) : List<LogEvent>

    protected fun <I> checkTx(id: Binary, data: Binary, signature: Signature<I, *>): I? {
        return if (id == signature.id()) {
            signature.`in`().decode(data, 0).value()
        } else {
            null
        }
    }
}