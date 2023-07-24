package com.rarible.ethereum.nft.domain

import com.rarible.ethereum.sign.domain.EIP712Domain
import scalether.domain.Address
import java.math.BigInteger

class EIP712DomainNftFactory(private val chainId: BigInteger) {

    fun createErc721Domain(token: Address): EIP712Domain {
        return createDomain(token, "Mint721")
    }

    fun createErc1155Domain(token: Address): EIP712Domain {
        return createDomain(token, "Mint1155")
    }

    private fun createDomain(token: Address, name: String): EIP712Domain {
        return EIP712Domain(
            name = name,
            version = "1",
            chainId = chainId,
            verifyingContract = token
        )
    }
}
