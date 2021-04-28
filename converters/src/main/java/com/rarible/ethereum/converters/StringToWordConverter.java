package com.rarible.ethereum.converters;

import io.daonomic.rpc.domain.Word;
import org.springframework.core.convert.converter.Converter;

public class StringToWordConverter implements Converter<String, Word> {
    @Override
    public Word convert(String source) {
        return Word.apply(source);
    }
}
