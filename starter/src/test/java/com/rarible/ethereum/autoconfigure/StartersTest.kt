package com.rarible.ethereum.autoconfigure

import com.rarible.ethereum.converters.EthUInt256ToHexStringConverter
import com.rarible.ethereum.converters.HexStringToEthUInt256Converter
import com.rarible.ethereum.converters.StringToEthAddressConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    properties = [
        "rarible.ethereum.converter.enabled=true",
        "rarible.ethereum.parity.httpUrl=localhost:6543"
    ]
)
@ActiveProfiles("core")
@Import(StartersTest.Configuration::class)
@EnableAutoConfiguration
class StartersTest {
    @Autowired
    private lateinit var ethUInt256ToHexStringConverter: EthUInt256ToHexStringConverter

    @Autowired
    private lateinit var hexStringToEthUInt256Converter: HexStringToEthUInt256Converter

    @Autowired
    private lateinit var stringToEthAddressConverter: StringToEthAddressConverter

    @Test
    fun test() {
        assertThat(ethUInt256ToHexStringConverter).isNotNull
        assertThat(hexStringToEthUInt256Converter).isNotNull
        assertThat(stringToEthAddressConverter).isNotNull
    }

    @TestConfiguration
    internal class Configuration
}
