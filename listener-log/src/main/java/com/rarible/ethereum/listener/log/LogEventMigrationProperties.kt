package com.rarible.ethereum.listener.log

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class LogEventMigrationProperties(
    @Value("\${createLogEventIndexContainingAddress:false}") val createLogEventIndexContainingAddress: Boolean,
    @Value("\${recalculateLogEventRaribleIndex:false}") val recalculateLogEventRaribleIndex: Boolean,
    @Value("\${copyFixedIndexToIndexField:false}") val copyFixedIndexToIndexField: Boolean,
    @Value("\${useNewMongoIndex:false}") val useNewIndex: Boolean,
    @Value("\${removeOldMongoIndex:false}") val removeOldMongoIndex: Boolean,

    @Value("\${markLogsRevertedForRevertedBlocks:false}") val markLogsRevertedForRevertedBlocks: Boolean
)