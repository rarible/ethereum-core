package com.rarible.ethereum.converters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HexStringToEthUInt256ConverterTest {
    private val convert = HexStringToEthUInt256Converter()

    @Test
    fun `should convert to hex string`() {
        val valueString = "0x000000000000000000000000000000000000000000000000000000000000000a"
        val ethUInt256 = convert.convert(valueString)
        assertThat(ethUInt256.value.intValueExact()).isEqualTo(10)
    }
}
