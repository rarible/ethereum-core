package com.rarible.ethereum.converters;

import org.springframework.core.convert.converter.Converter;
import scalether.domain.Address;

public class AddressToStringConverter implements Converter<Address, String> {
    @Override
    public String convert(Address source) {
        return source.toString();
    }
}
