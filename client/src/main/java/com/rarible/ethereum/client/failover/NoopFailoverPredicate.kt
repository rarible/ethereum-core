package com.rarible.ethereum.client.failover

import io.daonomic.rpc.domain.Response

class NoopFailoverPredicate : FailoverPredicate {
    override fun needFailover(response: Response<*>): Boolean {
        return false
    }
}
