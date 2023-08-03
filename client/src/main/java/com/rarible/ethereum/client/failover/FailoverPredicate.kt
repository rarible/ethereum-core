package com.rarible.ethereum.client.failover

import io.daonomic.rpc.domain.Response

interface FailoverPredicate {
    fun needFailover(response: Response<*>): Boolean
}
