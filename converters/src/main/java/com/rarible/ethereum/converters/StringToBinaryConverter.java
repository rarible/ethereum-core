package com.rarible.ethereum.converters;

import com.rarible.rpc.domain.Binary;
import org.springframework.core.convert.converter.Converter;

public class StringToBinaryConverter implements Converter<String, Binary> {
    @Override
    public Binary convert(String source) {
        return Binary.apply(source);
    }
}
