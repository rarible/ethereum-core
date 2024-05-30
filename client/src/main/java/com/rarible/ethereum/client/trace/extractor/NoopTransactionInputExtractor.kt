package com.rarible.ethereum.client.trace.extractor

import io.daonomic.rpc.domain.Binary

class NoopTransactionInputExtractor : TransactionInputExtractor {
    override fun extract(input: Binary): Binary {
        return input
    }
}
