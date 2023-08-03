package com.rarible.ethereum.client.failover

import io.daonomic.rpc.domain.Response

class CompositeFailoverPredicate(
    private val failoverPredicates: List<FailoverPredicate>,
) : FailoverPredicate {

    override fun needFailover(response: Response<*>): Boolean {
        return failoverPredicates.any { it.needFailover(response) }
    }
}
