package com.rarible.ethereum.listener.log.mock

import com.github.cloudyrock.spring.v5.EnableMongock
import com.rarible.ethereum.listener.log.EnableLogListeners
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

@EnableLogListeners(scanPackage = [TestLogConfiguration::class])
@EnableMongock
@EnableAutoConfiguration
class TestLogConfiguration