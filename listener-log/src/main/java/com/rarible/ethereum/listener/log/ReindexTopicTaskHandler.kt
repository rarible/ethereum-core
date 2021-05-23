package com.rarible.ethereum.listener.log

import com.rarible.core.task.TaskHandler
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import scalether.core.MonoEthereum

@Component
class ReindexTopicTaskHandler(
    private val logListenService: LogListenService,
    private val ethereum: MonoEthereum
) : TaskHandler<Long> {

    override val type: String
        get() = TOPIC

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        val topic = Word.apply(param)
        return fetchNormalBlockNumber()
            .flatMapMany { to -> reindexTopic(topic, from, to) }
            .map { it.first }
            .asFlow()
    }

    private fun reindexTopic(topic: Word, from: Long?, end: Long): Flux<LongRange> {
        return logListenService.reindex(topic, from ?: 1, end)
    }

    private fun fetchNormalBlockNumber() =
        ethereum.ethBlockNumber().map { it.toLong() }

    companion object {
        const val TOPIC = "TOPIC"
    }
}