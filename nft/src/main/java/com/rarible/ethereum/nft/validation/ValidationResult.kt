package com.rarible.ethereum.nft.validation

import scalether.domain.Address

sealed class ValidationResult {
    object Valid : ValidationResult()

    object InvalidCreatorAndSignatureSize : ValidationResult()

    object NotUniqCreators : ValidationResult()

    data class InvalidCreatorSignature(val creators: List<Address>) : ValidationResult()
}
