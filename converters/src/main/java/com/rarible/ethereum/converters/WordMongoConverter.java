package com.rarible.ethereum.converters;

import com.rarible.core.mongo.converter.SimpleMongoConverter;
import com.rarible.rpc.domain.Word;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WordMongoConverter implements SimpleMongoConverter<String, Word> {
    @Override
    public Converter<String, Word> getFromMongoConverter() {
        return new StringToWordConverter();
    }

    @Override
    public Converter<Word, String> getToMongoConverter() {
        return new WordToStringConverter();
    }

    @Override
    public boolean isSimpleType(Class<?> aClass) {
        return aClass == Word.class;
    }

    @Override
    public Optional<Class<?>> getCustomWriteTarget(Class<?> sourceType) {
        if (sourceType == Word.class) {
            return Optional.of(String.class);
        }
        return Optional.empty();
    }
}
