package com.rarible.ethereum.converters

import org.springframework.core.convert.converter.Converter
import scalether.domain.Address

class StringToEthAddressConverter : Converter<String, Address> {
    override fun convert(source: String): Address {
        return Address.apply(source)
    }
}