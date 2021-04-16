package com.rarible.ethereum.listener.log.persist

import com.rarible.core.mongo.configuration.EnableRaribleMongo
import com.rarible.core.mongo.configuration.IncludePersistProperties
import com.rarible.ethereum.converters.EnableScaletherMongoConversions
import org.springframework.context.annotation.Configuration

@Configuration
@EnableScaletherMongoConversions
@IncludePersistProperties
@EnableRaribleMongo(scanPackage = [PersistConfiguration::class])
class PersistConfiguration