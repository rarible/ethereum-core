package com.rarible.ethereum.client.mapper

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.daonomic.rpc.domain.Word

class WordDeserializer : StdDeserializer<Word>(Word::class.java) {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): Word =
        when (parser.currentToken) {
            JsonToken.VALUE_STRING -> {
                val text = parser.text.trim()
                if (text == "0x") {
                    ZERO
                } else {
                    Word.apply(text)
                }
            }
            else -> ctx.handleUnexpectedToken(_valueClass, parser) as Word
        }

    companion object {
        private val ZERO = Word.apply("0x0000000000000000000000000000000000000000000000000000000000000000")
    }
}

@JsonDeserialize(using = WordDeserializer::class)
abstract class WordMixin

fun ObjectMapper.registerWordDeserializer() {
    val module = SimpleModule()
    module.setMixInAnnotation(Word::class.java, WordMixin::class.java)
    registerModule(module)
}
