package com.rarible.ethereum.converters

import com.rarible.ethereum.domain.EthUInt256
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

@ReadingConverter
class HexStringToEthUInt256Converter : Converter<String, EthUInt256> {
    override fun convert(source: String): EthUInt256 {
        return EthUInt256.of(source)
    }
}