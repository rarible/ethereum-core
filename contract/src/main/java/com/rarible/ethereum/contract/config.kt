package com.rarible.ethereum.contract

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ContractServiceConfiguration::class)
annotation class EnableContractService

@Configuration
@EnableConfigurationProperties(EthereumContractProperties::class)
@ComponentScan(basePackageClasses = [ContractServiceConfiguration::class])
class ContractServiceConfiguration

internal const val RARIBLE_ETHEREUM_CONTRACT = "rarible.ethereum.contract"

@ConfigurationProperties(RARIBLE_ETHEREUM_CONTRACT)
@ConstructorBinding
data class EthereumContractProperties(
    val nativeDecimals: Int = 18
)
