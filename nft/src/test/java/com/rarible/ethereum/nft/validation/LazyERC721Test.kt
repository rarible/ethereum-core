package com.rarible.ethereum.nft.validation

import com.rarible.contracts.test.erc721.rarible.ERC721Rarible
import com.rarible.ethereum.nft.model.LazyERC721
import com.rarible.ethereum.nft.model.Part
import com.rarible.ethereum.sign.domain.EIP712Domain
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Word
import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3jold.crypto.Sign
import org.web3jold.utils.Numeric
import reactor.core.publisher.Mono
import scala.Tuple2
import scala.Tuple5
import scalether.abi.Uint256Type
import scalether.core.MonoEthereum
import scalether.domain.Address
import scalether.domain.response.TransactionReceipt
import scalether.java.Lists
import scalether.transaction.MonoGasPriceProvider
import scalether.transaction.MonoSigningTransactionSender
import scalether.transaction.MonoSimpleNonceProvider
import scalether.transaction.MonoTransactionPoller
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

@Testcontainers
internal class LazyERC721Test {
    private val ethereum = ethereum()

    @Test
    fun shouldValidateHash() = runBlocking<Unit> {
        val (key1, sender1) = signingTransactionSender()
        val (_, sender2) = signingTransactionSender()

        val contract = ERC721Rarible.deployAndWait(sender1, monoTransactionPoller()).awaitFirst()
        contract.__ERC721Rarible_init("T", "T", "ipfs:/", "https://none").execute().verifySuccess()
        contract.setDefaultApproval(sender2.from(), true).execute().verifySuccess()

        val domain = EIP712Domain("Mint721", "1", 17.toBigInteger(), contract.address())
        val tokenId = BigInteger.ONE.generateUint256(sender1.from())
        val uri = "/ipfs/QmWLsBu6nS4ovaHbGAXprD1qEssJu4r5taQfB74sCG51tp"
        val lazyErc721 = LazyERC721(
            token = contract.address(),
            tokenId = tokenId,
            uri = uri,
            creators = listOf(Part(sender1.from(), 10000)),
            royalties = listOf(),
            signatures = listOf()
        )
        val hashToSign = domain.hashToSign(lazyErc721.hash())
        val signature = hashToSign.sign(key1)
        contract.mintAndTransfer(
            Tuple5(
                tokenId,
                uri,
                arrayOf(Tuple2(sender1.from(), 10000.toBigInteger())),
                arrayOf(),
                arrayOf(signature.bytes())
            ),
            sender2.from()
        )
            .withSender(sender2)
            .execute()
            .verifySuccess()
    }

    private fun BigInteger.generateUint256(minter: Address?): BigInteger {
        assert(this < BigInteger.valueOf(2).pow(96)) { "tokenId size error" }
        val encoded = Uint256Type.encode(this).slice(20, 32)

        val binary = if (minter != null) {
            minter.add(encoded)
        } else {
            encoded
        }
        return Uint256Type.decode(binary, 0).value()
    }

    private fun signingTransactionSender(): Pair<BigInteger, MonoSigningTransactionSender> {
        val byteArray = ByteArray(32)
        ThreadLocalRandom.current().nextBytes(byteArray)
        val privateKey = Numeric.toBigInt(byteArray)

        return privateKey to MonoSigningTransactionSender(
            ethereum,
            MonoSimpleNonceProvider(ethereum),
            privateKey,
            BigInteger.valueOf(8000000),
            MonoGasPriceProvider { Mono.just(BigInteger.ZERO) }
        )
    }

    private fun monoTransactionPoller(): MonoTransactionPoller {
        return MonoTransactionPoller(ethereum)
    }

    private fun ethereumUrl(): URI {
        return URI.create("http://${ethereumContainer.host}:${ethereumContainer.getMappedPort(OPEN_ETHEREUM_HTTP_PORT)}")
    }

    private fun ethereum(): MonoEthereum {
        val transport = object : WebClientTransport(
            ethereumUrl().toASCIIString(),
            MonoEthereum.mapper(),
            Duration.ofSeconds(30).toMillis().toInt(),
            Duration.ofSeconds(30).toMillis().toInt()
        ) {
            override fun maxInMemorySize(): Int = 50000
        }
        return MonoEthereum(transport)
    }

    private suspend fun Mono<Word>.waitReceipt(): TransactionReceipt {
        val value = this.awaitFirstOrNull()
        require(value != null) { "txHash is null" }
        return ethereum.ethGetTransactionReceipt(value).awaitFirst().get()
    }

    private suspend fun Mono<Word>.verifySuccess(): TransactionReceipt {
        val receipt = waitReceipt()
        Assertions.assertTrue(receipt.success()) {
            val result = ethereum.executeRaw(
                Request(
                    1,
                    "trace_replayTransaction",
                    Lists.toScala(
                        receipt.transactionHash().toString(),
                        Lists.toScala("trace")
                    ),
                    "2.0"
                )
            ).block()!!
            "traces: ${result.result().get()}"
        }
        return receipt
    }

    private fun Word.sign(privateKey: BigInteger): Binary {
        val publicKey = Sign.publicKeyFromPrivate(privateKey)
        return Sign.signMessageHash(bytes(), publicKey, privateKey).toBinary()
    }

    private fun Sign.SignatureData.toBinary(): Binary = Binary.apply(this.r).add(this.s).add(byteArrayOf(this.v))

    companion object {
        const val OPEN_ETHEREUM_HTTP_PORT: Int = 8545

        private val ethereumContainer: KGenericContainer by lazy {
            KGenericContainer("rarible/openethereum:1.0.59").apply {
                withExposedPorts(OPEN_ETHEREUM_HTTP_PORT)
                withCommand("--network-id 18 --chain /home/openethereum/.local/share/config/openethereum/chain.json --jsonrpc-interface all --unsafe-expose")
                waitingFor(Wait.defaultWaitStrategy())
            }
        }

        init {
            ethereumContainer.start()
        }

        class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)
    }
}
