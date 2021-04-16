package com.rarible.ethereum.contract.repository

import com.rarible.ethereum.contract.model.Contract
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.findById
import scalether.domain.Address

class ContractRepository(
    private val template: ReactiveMongoTemplate
) {
    suspend fun save(contract: Contract): Contract {
        return template.save(contract).awaitFirst()
    }

    suspend fun findById(id: Address): Contract? {
        return template.findById<Contract>(id).awaitFirstOrNull()
    }
}