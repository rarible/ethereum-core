package com.rarible.ethereum.converters;

import io.daonomic.rpc.domain.Binary;
import org.springframework.core.convert.converter.Converter;

public class BinaryToStringConverter implements Converter<Binary, String> {
    @Override
    public String convert(Binary source) {
        return source.toString();
    }
}
