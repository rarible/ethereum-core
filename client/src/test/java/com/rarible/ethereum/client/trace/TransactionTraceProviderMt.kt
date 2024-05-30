package com.rarible.ethereum.client.trace

import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import scalether.core.MonoEthereum
import scalether.domain.Address

@Tag("manual")
@Disabled
class TransactionTraceProviderMt {

    private val MAINNET_NODE = "PUT_HERE_SOME_PROD_URL_SUPPORS_TRACING"

    private val ethereum = MonoEthereum(object : WebClientTransport(
        MAINNET_NODE,
        MonoEthereum.mapper(),
        60000,
        60000
    ) {
        override fun maxInMemorySize(): Int = 100000000
    })

    @Test
    fun `find all traces - geth`() = runBlocking<Unit> {
        val provider = GethTransactionTraceProvider(ethereum)
        val traceResult = provider.traceAndFindAllCallsTo(
            Word.apply("0x3163b526e47333c9e66affb3124544e963ef0126bf9c6a3abfdaf30dd47efd7f"),
            Address.apply("0x7f268357a8c2552623316e2562d90e642bb538e5"),
            setOf(Binary.apply("0xab834bab"))
        )
        // TODO Have no idea why it differs from openethereum
        assertThat(traceResult.count()).isEqualTo(20)
    }

    @Test
    fun `find all traces - openethereum`() = runBlocking<Unit> {
        val provider = OpenEthereumTransactionTraceProvider(ethereum)
        val traceResult = provider.traceAndFindAllCallsTo(
            Word.apply("0x3163b526e47333c9e66affb3124544e963ef0126bf9c6a3abfdaf30dd47efd7f"),
            Address.apply("0x7f268357a8c2552623316e2562d90e642bb538e5"),
            setOf(Binary.apply("0xab834bab"))
        )
        assertThat(traceResult.count()).isEqualTo(18)
    }

    @Test
    fun `find traces of type - geth`() = runBlocking<Unit> {
        val provider = GethTransactionTraceProvider(ethereum)
        val traceResult = provider.traceAndFindAllCallsOfType(
            transactionHash = Word.apply("0xdc74e760e588dc74310fe2da04860e069173c4f6b30980d9970066c3dea8ef3d"),
            callTypes = setOf("create", "create2")
        )
        assertThat(traceResult).hasSize(1)
        assertThat(traceResult[0].type).isEqualTo("CREATE2")
        assertThat(traceResult[0].from.prefixed()).isEqualTo("0x07cf8f81852a58dd26fa19e69545f72e263347e5")
        assertThat(traceResult[0].to?.prefixed()).isEqualTo("0x0000000000001b84b1cb32787b0d64758d019317")
    }

    @Test
    fun `find traces of type - openethereum`() = runBlocking<Unit> {
        val provider = OpenEthereumTransactionTraceProvider(ethereum)
        val traceResult = provider.traceAndFindAllCallsOfType(
            transactionHash = Word.apply("0xdc74e760e588dc74310fe2da04860e069173c4f6b30980d9970066c3dea8ef3d"),
            callTypes = setOf("create", "create2")
        )
        assertThat(traceResult).hasSize(1)
        assertThat(traceResult[0].type).isEqualTo("create")
        assertThat(traceResult[0].from.prefixed()).isEqualTo("0x07cf8f81852a58dd26fa19e69545f72e263347e5")
        assertThat(traceResult[0].to?.prefixed()).isEqualTo("0x0000000000001b84b1cb32787b0d64758d019317")
    }
}
