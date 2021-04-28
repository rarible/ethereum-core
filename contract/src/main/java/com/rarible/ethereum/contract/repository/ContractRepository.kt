package com.rarible.ethereum.contract.repository

import com.rarible.core.contract.model.Contract
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service
import scalether.domain.Address

@Service
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