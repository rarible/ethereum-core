package com.rarible.ethereum.client.trace.extractor

import io.daonomic.rpc.domain.Binary

interface TransactionInputExtractor {
    fun extract(input: Binary): Binary
}
