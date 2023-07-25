package com.rarible.ethereum.converters

import com.rarible.ethereum.domain.EthUInt256
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EthUInt256ToHexStringConverterTest {
    private val convert = EthUInt256ToHexStringConverter()

    @Test
    fun `should convert to hex string`() {
        val value = EthUInt256.of("10")
        val valueString = convert.convert(value)
        assertThat(valueString).isEqualTo("0x000000000000000000000000000000000000000000000000000000000000000a")
    }
}
