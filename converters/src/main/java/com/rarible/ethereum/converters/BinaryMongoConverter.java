package com.rarible.ethereum.converters;

import com.rarible.core.mongo.converter.SimpleMongoConverter;
import io.daonomic.rpc.domain.Binary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BinaryMongoConverter implements SimpleMongoConverter<String, Binary> {
    @Override
    public Converter<String, Binary> getFromMongoConverter() {
        return new StringToBinaryConverter();
    }

    @Override
    public Converter<Binary, String> getToMongoConverter() {
        return new BinaryToStringConverter();
    }

    @Override
    public boolean isSimpleType(Class<?> aClass) {
        return aClass == Binary.class;
    }

    @Override
    public Optional<Class<?>> getCustomWriteTarget(Class<?> sourceType) {
        if (sourceType == Binary.class) {
            return Optional.of(String.class);
        }
        return Optional.empty();
    }
}
