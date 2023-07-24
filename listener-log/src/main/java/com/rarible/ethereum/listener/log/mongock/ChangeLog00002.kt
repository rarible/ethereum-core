package com.rarible.ethereum.listener.log.mongock

import com.github.cloudyrock.mongock.ChangeLog
import com.github.cloudyrock.mongock.ChangeSet
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.ethereum.listener.log.domain.BlockHead
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.index.Index

@ChangeLog(order = "00002")
class ChangeLog00002 {

    @ChangeSet(id = "ChangeLog00002.addBlockIndex", order = "0000", author = "eugene")
    fun addBlockIndex(template: MongockTemplate) {
        template.indexOps(BlockHead::class.java).ensureIndex(
            Index()
                .on("status", Sort.Direction.ASC)
        )
        template.indexOps(BlockHead::class.java).ensureIndex(
            Index()
                .on("hash", Sort.Direction.ASC)
        )
    }
}
