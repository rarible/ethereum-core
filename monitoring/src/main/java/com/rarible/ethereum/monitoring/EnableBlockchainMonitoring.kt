package com.rarible.ethereum.monitoring

import org.springframework.context.annotation.Import

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Import(BlockchainMonitoringConfiguration::class)
annotation class EnableBlockchainMonitoring
