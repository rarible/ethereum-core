package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.converters.EthUInt256ToHexStringConverter
import com.rarible.ethereum.converters.HexStringToEthUInt256Converter
import com.rarible.ethereum.converters.StringToEthAddressConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@ConditionalOnProperty(prefix = RARIBLE_CORE_ETHEREUM_DOMAIN_CONVERTER, name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(EthereumDomainConvertersProperties::class)
class EthereumDomainConvertersAutoConfiguration {
    @Bean
    fun ethStringToEthAddressConverter(): StringToEthAddressConverter {
        return StringToEthAddressConverter()
    }

    @Bean
    fun hexStringToEthUInt256Converter(): HexStringToEthUInt256Converter {
        return HexStringToEthUInt256Converter()
    }

    @Bean
    fun ethUInt256ToHexStringConverter(): EthUInt256ToHexStringConverter {
        return EthUInt256ToHexStringConverter()
    }
}