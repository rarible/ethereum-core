package com.rarible.ethereum.client.failover

import io.daonomic.rpc.domain.Response
import org.slf4j.LoggerFactory
import scala.jdk.javaapi.OptionConverters

class SimplePredicate(
    private val code: Int,
    private val errorMessagePrefix: String
) : FailoverPredicate {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun needFailover(response: Response<*>): Boolean {
        val error = OptionConverters.toJava(response.error()).orElse(null)
        val responseCode = error?.code()
        val responseMessage = error?.message()

        return (responseCode == code && responseMessage?.startsWith(errorMessagePrefix) == true).also {
            if (it) logger.info("Failover cause detected: code=$code, msg=$responseMessage")
        }
    }
}
