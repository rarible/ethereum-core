package com.rarible.ethereum.client.failover

import io.daonomic.rpc.domain.Error
import io.daonomic.rpc.domain.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import scala.Option

internal class SimplePredicateTest {
    private val predicate = SimplePredicate(code = -32000, errorMessagePrefix = "test message")

    @ParameterizedTest
    @MethodSource("testCases")
    fun failover(case: TestCase) {
        assertThat(predicate.needFailover(case.response)).isEqualTo(case.expected)
    }

    companion object {
        @JvmStatic
        fun testCases() = listOf(
            TestCase(
                name = "Code and message match",
                response = Response(
                    0L,
                    Option.apply(""),
                    Option.apply(
                        Error(
                            -32000,
                            "test message test",
                            Option.apply("")
                        )
                    ),
                ),
                expected = true,
            ),
            TestCase(
                name = "Code match and message not match",
                response = Response(
                    0L,
                    Option.apply(""),
                    Option.apply(
                        Error(
                            -32000,
                            "test1 message test",
                            Option.apply("")
                        )
                    ),
                ),
                expected = false,
            ),
            TestCase(
                name = "Code not match and message match",
                response = Response(
                    0L,
                    Option.apply(""),
                    Option.apply(
                        Error(
                            -32001,
                            "test message test",
                            Option.apply("")
                        )
                    ),
                ),
                expected = false,
            ),
            TestCase(
                name = "No error",
                response = Response(
                    0L,
                    Option.apply("")
                ),
                expected = false,
            )
        )
    }

    data class TestCase(
        val name: String,
        val response: Response<*>,
        val expected: Boolean,
    )
}
