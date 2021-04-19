package com.rarible.ethereum.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EthUInt256Test {
    @Test
    fun `should compare correctly`() {
        assertThat(EthUInt256.of(10) > EthUInt256.of(5)).isTrue()
        assertThat(EthUInt256.of(1) > EthUInt256.of(2)).isFalse()
    }

    @Test
    fun serialize() {
        val mapper = ObjectMapper()
        assertThat(mapper.writeValueAsString(SimpleData(EthUInt256.TEN)))
            .isEqualTo("{\"uint\":\"0x000000000000000000000000000000000000000000000000000000000000000a\"}")
    }

    @Test
    fun deserialize() {
        val mapper = ObjectMapper().registerKotlinModule()
        assertThat(mapper.readValue("{\"uint\":\"0x000000000000000000000000000000000000000000000000000000000000000a\"}", SimpleData::class.java))
            .isEqualTo(SimpleData(EthUInt256.TEN))

        assertThat(mapper.readValue("{\"uint\":\"10\"}", SimpleData::class.java))
            .isEqualTo(SimpleData(EthUInt256.TEN))
        assertThat(mapper.readValue("{\"uint\":10}", SimpleData::class.java))
            .isEqualTo(SimpleData(EthUInt256.TEN))
    }
}

data class SimpleData(val uint: EthUInt256)