package com.rarible.ethereum.listener.log

import com.rarible.core.test.ext.EthereumTest
import com.rarible.core.test.ext.MongoCleanup
import com.rarible.core.test.ext.MongoTest
import com.rarible.ethereum.listener.log.mock.TestLogConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

@EthereumTest
@MongoTest
@MongoCleanup
@SpringBootTest
@ContextConfiguration(classes = [TestLogConfiguration::class, EthereumConfigurationIntr::class])
@EnableAutoConfiguration
@ActiveProfiles("integration")
annotation class IntegrationTest
