package com.rarible.ethereum.converters;

import io.daonomic.rpc.domain.Word;
import org.springframework.core.convert.converter.Converter;

public class WordToStringConverter implements Converter<Word, String> {
    @Override
    public String convert(Word source) {
        return source.toString();
    }
}
