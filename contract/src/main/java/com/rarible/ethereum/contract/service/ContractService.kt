package com.rarible.ethereum.contract.service

import com.rarible.contracts.erc165.IERC165
import com.rarible.contracts.erc20.IERC20
import com.rarible.contracts.erc721.IERC721
import com.rarible.core.common.optimisticLock
import com.rarible.core.contract.model.Contract
import com.rarible.core.contract.model.Erc1155Token
import com.rarible.core.contract.model.Erc20Token
import com.rarible.core.contract.model.Erc721Token
import com.rarible.ethereum.contract.repository.ContractRepository
import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Binary
import kotlinx.coroutines.reactive.awaitFirst
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import scalether.domain.Address
import scalether.transaction.MonoTransactionSender
import java.math.BigInteger
import kotlin.math.log

@Service
class ContractService(
    private val contractRepository: ContractRepository,
    private val sender: MonoTransactionSender
) {
    suspend fun get(address: Address): Contract =
        optimisticLock {
            val found = contractRepository.findById(address)
            if (found != null) {
                found
            } else {
                val fetched = fetch(address)
                contractRepository.save(fetched)
            }
        }

    suspend fun fetch(address: Address): Contract {
        val erc165 = IERC165(address, sender)

        return when {
            isSupportedInterface(ERC721, erc165)
                || isSupportedInterface(ERC721_DEPRECATED1, erc165)
                || isSupportedInterface(ERC721_DEPRECATED2, erc165)
            -> {
                val erc721 = IERC721(address, sender)
                Erc721Token(
                    id = address,
                    name = erc721.name().tryAwaitMethodCall(),
                    symbol = erc721.symbol().tryAwaitMethodCall()
                )
            }
            isSupportedInterface(ERC1155, erc165) -> {
                Erc1155Token(
                    id = address,
                    name = null,
                    symbol = null
                )
            }
            else -> {
                val erc20 = IERC20(address, sender)
                Erc20Token(
                    id = address,
                    name = erc20.name().tryAwaitMethodCall(),
                    symbol = erc20.symbol().tryAwaitMethodCall(),
                    decimals = erc20.decimals().tryAwaitMethodCall()?.let { parseDecimals(address, it) }
                )
            }
        }
    }

    private fun parseDecimals(contract: Address, decimals: BigInteger): Int? {
        return when {
            decimals < BigInteger.ZERO -> {
                logger.warn("Contract $contract has negative decimals: $decimals")
                null
            }
            decimals > BigInteger.valueOf(Int.MAX_VALUE.toLong()) -> {
                logger.warn("Contract $contract has infinite decimals: $decimals")
                null
            }
            else -> {
                decimals.intValueExact()
            }
        }
    }

    private suspend fun isSupportedInterface(interfaceId: Binary, erc165: IERC165): Boolean {
        return erc165.supportsInterface(interfaceId.bytes()).tryAwaitMethodCall() ?: false
    }

    suspend fun <T> Mono<T>.tryAwaitMethodCall(): T? {
        return try {
            awaitFirst()
        } catch (ex: RpcCodeException) {
            null
        } catch (ex: IllegalArgumentException) {
            null
        } catch (ex: Exception) {
            throw IllegalStateException("Can't get method call result", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContractService::class.java)
        val ERC721: Binary = Binary.apply("0x80ac58cd")
        val ERC721_DEPRECATED1: Binary = Binary.apply("0xd31b620d")
        val ERC721_DEPRECATED2: Binary = Binary.apply("0x80ac58cd")
        val CK: Binary = ERC721_DEPRECATED2
        val ERC1155: Binary = Binary.apply("0xd9b67a26")
    }
}
