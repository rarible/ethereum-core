package com.rarible.ethereum.sign.service

class CheckSignatureException(message: String, cause: Exception) : RuntimeException(message, cause)

class InvalidSignatureException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
