package com.rarible.ethereum.converters

import com.rarible.ethereum.domain.EthUInt256
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import scalether.abi.Uint256Type

@WritingConverter
class EthUInt256ToHexStringConverter : Converter<EthUInt256, String> {
    override fun convert(source: EthUInt256): String {
        return Uint256Type.encode(source.value).prefixed()
    }
}
