package com.rarible.ethereum.nft.validation

import com.rarible.ethereum.nft.domain.EIP712DomainNftFactory
import com.rarible.ethereum.nft.model.LazyERC1155
import com.rarible.ethereum.nft.model.LazyERC721
import com.rarible.ethereum.nft.model.LazyNft
import com.rarible.ethereum.sign.service.ERC1271SignService
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LazyNftValidator(
    private val erc1271SignService: ERC1271SignService,
    private val eip712DomainNftFactory: EIP712DomainNftFactory
) {
    suspend fun validate(lazyNft: LazyNft): ValidationResult {
        val creators = lazyNft.creators
        val signatures = lazyNft.signatures

        if (creators.size != signatures.size) {
            return ValidationResult.InvalidCreatorAndSignatureSize
        }
        if (creators.toHashSet().size != creators.size) {
            return ValidationResult.NotUniqCreators
        }

        val hashToSign = hashToSign(lazyNft)

        return coroutineScope {
            val results = creators.mapIndexed { index, creator ->
                async {
                    val signature = signatures[index]

                    if (erc1271SignService.isSigner(creator.account, hashToSign, signature).not())
                        ValidationResult.InvalidCreatorSignature(listOf(creator.account))
                    else ValidationResult.Valid
                }
            }
            results.awaitAll().fold<ValidationResult, ValidationResult>(ValidationResult.Valid) { acc, result ->
                if (result is ValidationResult.InvalidCreatorSignature) {
                    val otherInvalidCreators = (acc as? ValidationResult.InvalidCreatorSignature)?.creators ?: emptyList()
                    ValidationResult.InvalidCreatorSignature(result.creators + otherInvalidCreators)
                } else {
                    acc
                }
            }
        }
    }

    private fun hashToSign(lazyNft: LazyNft): Word {
        val domain = when (lazyNft) {
            is LazyERC721 -> eip712DomainNftFactory.createErc721Domain(lazyNft.token)
            is LazyERC1155 -> eip712DomainNftFactory.createErc1155Domain(lazyNft.token)
        }
        return domain.hashToSign(lazyNft.hash())
    }
}

