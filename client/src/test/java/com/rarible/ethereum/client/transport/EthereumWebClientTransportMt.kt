package com.rarible.ethereum.client.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import scalether.core.MonoEthereum

@Disabled("manual")
internal class EthereumWebClientTransportMt {

    @Test
    fun `text plain mediaType`() {
        val ethereum = MonoEthereum(
            EthereumWebClientTransport(
                rpcUrl = "https://rpc.hyperliquid-testnet.xyz/evm",
                mapper = MonoEthereum.mapper(),
                mediaType = MediaType.TEXT_PLAIN,
            )
        )

        val blockNumber = ethereum.ethBlockNumber().block()
        assertThat(blockNumber.toLong()).isGreaterThan(0)

        val block = ethereum.ethGetBlockByNumber(blockNumber).block()
        assertThat(block.number()).isEqualTo(blockNumber)
    }
}
