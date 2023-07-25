package com.rarible.ethereum.listener.log.mongock

import com.github.cloudyrock.mongock.ChangeLog
import com.github.cloudyrock.mongock.ChangeSet
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate
import com.rarible.core.task.Task
import com.rarible.core.task.TaskStatus
import com.rarible.ethereum.listener.log.ReindexTopicTaskHandler
import org.bson.Document
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

@ChangeLog(order = "00003")
class ChangeLog00003 {
    @ChangeSet(id = "ChangeLog00003.convertInitializeStatusToTask", order = "0001", author = "eugene")
    fun convertInitializeStatusToTask(mongockTemplate: MongockTemplate) {
        mongockTemplate.find(Query(), Document::class.java, "initializeStatus")
            .forEach {
                val param = it.getString("_id")
                val criteria = Criteria().andOperator(
                    Task::type isEqualTo ReindexTopicTaskHandler.TOPIC,
                    Task::param isEqualTo param
                )
                val found = mongockTemplate.findOne<Task>(Query(criteria))
                if (found == null) {
                    val taskStatus = when (it.getString("status")) {
                        "COMPLETED" -> TaskStatus.COMPLETED
                        else -> TaskStatus.NONE
                    }
                    val newTask = Task(
                        type = ReindexTopicTaskHandler.TOPIC,
                        param = param,
                        lastStatus = taskStatus,
                        state = it.getLong("reindexEndBlockNumber"),
                        running = false
                    )
                    mongockTemplate.save(newTask)
                }
            }
    }
}
