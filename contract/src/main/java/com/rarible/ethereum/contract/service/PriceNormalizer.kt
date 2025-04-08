package com.rarible.ethereum.contract.service

import com.rarible.core.contract.model.Erc20Token
import com.rarible.ethereum.contract.EthereumContractProperties
import org.springframework.stereotype.Component
import scalether.domain.Address
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

@Component
class PriceNormalizer(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val contractService: ContractService,
    private val properties: EthereumContractProperties,
) {
    // There are not a lot of Erc20 contracts and they can't be updated in runtime,
    // so we can store decimal count in cache in order to avoid DB access on each call
    private val erc20Cache = ConcurrentHashMap<Address, Int>()

    suspend fun normalize(address: Address, value: BigInteger): BigDecimal {
        return value.toBigDecimal(getErc20Decimals(address))
    }

    private suspend fun getErc20Decimals(token: Address): Int {
        if (token == Address.ZERO()) {
            return properties.nativeDecimals
        }
        var result = erc20Cache[token]
        if (result == null) {
            result = (contractService.get(token) as Erc20Token).decimals ?: 0
            erc20Cache[token] = result
        }
        return result
    }
}
