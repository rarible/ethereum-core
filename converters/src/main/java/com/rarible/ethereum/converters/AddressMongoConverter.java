package com.rarible.ethereum.converters;

import com.rarible.core.mongo.converter.SimpleMongoConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import scalether.domain.Address;

import java.util.Optional;

@Component
public class AddressMongoConverter implements SimpleMongoConverter<String, Address> {
    @Override
    public Converter<String, Address> getFromMongoConverter() {
        return new StringToAddressConverter();
    }

    @Override
    public Converter<Address, String> getToMongoConverter() {
        return new AddressToStringConverter();
    }

    @Override
    public boolean isSimpleType(Class<?> aClass) {
        return aClass == Address.class;
    }

    @Override
    public Optional<Class<?>> getCustomWriteTarget(Class<?> sourceType) {
        if (sourceType == Address.class) {
            return Optional.of(String.class);
        }
        return Optional.empty();
    }
}
