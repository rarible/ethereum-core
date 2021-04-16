package com.rarible.ethereum.converters;

import com.rarible.rpc.domain.Binary;
import org.springframework.core.convert.converter.Converter;

public class BinaryToStringConverter implements Converter<Binary, String> {
    @Override
    public String convert(Binary source) {
        return source.toString();
    }
}
