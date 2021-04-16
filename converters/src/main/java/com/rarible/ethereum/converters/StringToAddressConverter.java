package com.rarible.ethereum.converters;

import org.springframework.core.convert.converter.Converter;
import scalether.domain.Address;

public class StringToAddressConverter implements Converter<String, Address> {
    @Override
    public Address convert(String source) {
        return Address.apply(source);
    }
}
