package com.rarible.ethereum.converters

import com.rarible.core.mongo.converter.SimpleMongoConverter
import com.rarible.ethereum.domain.EthUInt256
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import java.util.*

@Component
class EthUInt256MongoConverter : SimpleMongoConverter<String, EthUInt256> {
    override fun isSimpleType(aClass: Class<*>): Boolean {
        return String::class.java == aClass
    }

    override fun getCustomWriteTarget(sourceType: Class<*>): Optional<Class<*>> {
        return if (sourceType == EthUInt256::class.java) {
            Optional.of(String::class.java)
        } else Optional.empty()
    }

    override fun getFromMongoConverter(): Converter<String, EthUInt256> {
        return HexStringToEthUInt256Converter()
    }

    override fun getToMongoConverter(): Converter<EthUInt256, String> {
        return EthUInt256ToHexStringConverter()
    }
}