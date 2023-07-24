package com.rarible.ethereum.domain

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import scalether.abi.Uint256Type
import java.math.BigInteger

@JsonSerialize(using = EthUInt256.EthUint256Serializer::class)
@JsonDeserialize(using = EthUInt256.EthUint256Deserializer::class)
data class EthUInt256(val value: BigInteger) : Comparable<EthUInt256> {

    operator fun plus(other: EthUInt256): EthUInt256 {
        return copy(value = value.add(other.value))
    }

    operator fun minus(other: EthUInt256): EthUInt256 {
        return copy(value = value.subtract(other.value))
    }

    operator fun plus(other: BigInteger): EthUInt256 {
        return copy(value = value.add(other))
    }

    operator fun minus(other: BigInteger): EthUInt256 {
        return copy(value = value.subtract(other))
    }

    operator fun times(other: BigInteger): EthUInt256 {
        return copy(value = value.multiply(other))
    }

    operator fun times(other: EthUInt256): EthUInt256 {
        return copy(value = value.multiply(other.value))
    }

    operator fun div(other: BigInteger): EthUInt256 {
        return copy(value = value.divide(other))
    }

    operator fun div(other: EthUInt256): EthUInt256 {
        return copy(value = value.divide(other.value))
    }

    fun pow(n: Int): EthUInt256 {
        return copy(value = value.pow(n))
    }

    override fun compareTo(other: EthUInt256): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String {
        return Uint256Type.encode(value).prefixed()
    }

    companion object {
        val ZERO = of(0)
        val ONE = of(1)
        val TEN = of(10)

        fun of(value: Long): EthUInt256 {
            return EthUInt256(BigInteger.valueOf(value))
        }

        fun of(value: Int): EthUInt256 {
            return EthUInt256(BigInteger.valueOf(value.toLong()))
        }

        fun of(value: String): EthUInt256 {
            return if (value.startsWith("0x")) {
                EthUInt256(Uint256Type.decode(Binary.apply(value), 0).value())
            } else if (value.endsWith(".0")) {
                EthUInt256(BigInteger(value.substring(0, value.length - 2), 10))
            } else {
                EthUInt256(BigInteger(value, 10))
            }
        }

        fun of(value: BigInteger): EthUInt256 {
            return EthUInt256(value)
        }

        fun of(value: Word): EthUInt256 {
            return EthUInt256(value.toBigInteger())
        }
    }

    class EthUint256Deserializer : StdScalarDeserializer<EthUInt256>(EthUInt256::class.java) {
        override fun deserialize(parser: JsonParser, c: DeserializationContext): EthUInt256 {
            return when (parser.currentToken) {
                JsonToken.VALUE_STRING -> of(parser.text.trim())
                JsonToken.VALUE_NUMBER_INT -> of(parser.text.trim())
                else -> c.handleUnexpectedToken(_valueClass, parser) as EthUInt256
            }
        }
    }

    class EthUint256Serializer : StdScalarSerializer<EthUInt256>(EthUInt256::class.java) {
        override fun serialize(uint: EthUInt256, gen: JsonGenerator, p2: SerializerProvider) {
            gen.writeString(uint.toString())
        }
    }
}
