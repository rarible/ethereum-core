package com.rarible.ethereum.sign.service

class CheckSignatureException(message: String, cause: Exception) : RuntimeException(message, cause)

class InvalidSignatureException(message: String, cause: Exception) : RuntimeException(message, cause)