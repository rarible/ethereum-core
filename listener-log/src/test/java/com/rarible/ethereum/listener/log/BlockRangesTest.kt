package com.rarible.ethereum.listener.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockRangesTest {
    @Test
    fun ranges() {
        assertEquals(
            BlockRanges.getRanges(1, 9, 10).collectList().block()!!,
            listOf(1L..9L)
        )
        assertEquals(
            BlockRanges.getRanges(1, 10, 10).collectList().block()!!,
            listOf(1L..10L)
        )
        assertEquals(
            BlockRanges.getRanges(1, 11, 10).collectList().block()!!,
            listOf(1L..10L, 11L..11)
        )
    }
}
