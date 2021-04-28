package com.rarible.ethereum.contract.service

import com.rarible.contracts.test.erc1155.TestERC1155
import com.rarible.contracts.test.erc20.TestERC20
import com.rarible.contracts.test.erc721.TestERC721
import com.rarible.core.test.containers.MongodbReactiveBaseTest
import com.rarible.core.test.containers.OpenEthereumTestContainer
import com.rarible.core.contract.model.ContractType
import com.rarible.ethereum.contract.repository.ContractRepository
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class ContractServiceTest : MongodbReactiveBaseTest() {
    private val ethereumTestContainer = OpenEthereumTestContainer()
    private val repository = ContractRepository(createReactiveMongoTemplate())
    private val contractService = ContractService(repository, ethereumTestContainer.readOnlyTransactionSender())

    @Test
    fun `fetch and save erc721 contract`() = runBlocking<Unit> {
        val userSender = ethereumTestContainer.signingTransactionSender()
        val poller = ethereumTestContainer.monoTransactionPoller()

        val name = "Test ERC721"
        val symbol = "ERC721 Symbol"
        val contract = TestERC721.deployAndWait(userSender, poller, name, symbol).awaitFirst()

        val result = contractService.get(contract.address())
        assertThat(result.type).isEqualTo(ContractType.ERC721_TOKEN)
        assertThat(result.name).isEqualTo(name)
        assertThat(result.symbol).isEqualTo(symbol)

        val savedContract = repository.findById(contract.address())
        assertThat(savedContract).isEqualTo(result)
    }

    @Test
    fun `fetch and save erc1155 contract`() = runBlocking<Unit> {
        val userSender = ethereumTestContainer.signingTransactionSender()
        val poller = ethereumTestContainer.monoTransactionPoller()

        val name = "Test ERC1155"
        val symbol = "ERC1155 Symbol"
        val contract = TestERC1155.deployAndWait(userSender, poller, name).awaitFirst()

        val result = contractService.get(contract.address())
        assertThat(result.type).isEqualTo(ContractType.ERC1155_TOKEN)
        assertThat(result.name).isEqualTo(null)
        assertThat(result.symbol).isEqualTo(null)

        val savedContract = repository.findById(contract.address())
        assertThat(savedContract).isEqualTo(result)
    }

    @Test
    fun `fetch and save erc20 contract`() = runBlocking<Unit> {
        val userSender = ethereumTestContainer.signingTransactionSender()
        val poller = ethereumTestContainer.monoTransactionPoller()

        val name = "Test ERC20"
        val symbol = "ERC20 Symbol"
        val contract = TestERC20.deployAndWait(userSender, poller, name, symbol).awaitFirst()

        val result = contractService.get(contract.address())
        assertThat(result.type).isEqualTo(ContractType.ERC20_TOKEN)
        assertThat(result.name).isEqualTo(name)
        assertThat(result.symbol).isEqualTo(symbol)

        val savedContract = repository.findById(contract.address())
        assertThat(savedContract).isEqualTo(result)
    }
}

