package com.rarible.ethereum.contract

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ContractServiceConfiguration::class)
annotation class EnableContractService

@Configuration
@ComponentScan(basePackageClasses = [ContractServiceConfiguration::class])
class ContractServiceConfiguration
