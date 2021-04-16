package com.rarible.ethereum.listener.log

import com.rarible.core.task.TaskHandler
import com.rarible.ethereum.listener.log.persist.BlockRepository
import com.rarible.rpc.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class ReindexTopicTaskHandler(
    private val logListenService: LogListenService,
    private val blockRepository: BlockRepository
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
        blockRepository.findFirstByIdAsc()
            .map { it.id }

    companion object {
        const val TOPIC = "TOPIC"
    }
}