package com.rarible.ethereum.listener.log.persist

import com.rarible.ethereum.listener.log.domain.CheckedBlock
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class RevertedLogStateRepository(
    private val mongo: ReactiveMongoOperations
) {
    suspend fun get(): CheckedBlock? {
        val criteria = Criteria.where("_id").isEqualTo(STATE_ID)
        return mongo.findOne<State>(Query.query(criteria), COLLECTION).awaitFirstOrNull()?.checkedBlock
    }

    suspend fun save(checkedBlock: CheckedBlock) {
        mongo.save(State(checkedBlock), COLLECTION).awaitFirst()
    }

    private data class State(
        val checkedBlock: CheckedBlock?,
        @Id
        val id: String = STATE_ID
    )

    private companion object {
        const val STATE_ID = "reverted_log_check_state_id"
        const val COLLECTION = "log_check_state"
    }
}