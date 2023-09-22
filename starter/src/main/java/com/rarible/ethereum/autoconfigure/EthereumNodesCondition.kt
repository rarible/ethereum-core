package com.rarible.ethereum.autoconfigure

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(EthereumNodesCondition::class)
annotation class ConditionalOnEthereumNodesProperty

class EthereumNodesCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val ethereumNodeProperty = context.environment.getProperty("$RARIBLE_ETHEREUM.nodes[0].http-url")
        val legacyEthereumNodeProperty = context.environment.getProperty("$RARIBLE_ETHEREUM.http-url")
        return ethereumNodeProperty != null || legacyEthereumNodeProperty != null
    }
}
