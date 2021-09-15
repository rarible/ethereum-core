package com.rarible.ethereum.sign.service

import com.rarible.contracts.test.erc1271.TestERC1271
import com.rarible.core.test.ext.EthereumTest
import com.rarible.core.test.ext.EthereumTestExtension
import com.rarible.ethereum.common.generateNewKeys
import com.rarible.ethereum.common.keccak256
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import io.daonomic.rpc.mono.WebClientTransport
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import reactor.core.publisher.Mono
import scalether.core.MonoEthereum
import scalether.domain.Address
import scalether.domain.response.TransactionReceipt
import scalether.transaction.*
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

@EthereumTest
internal class ERC1271SignServiceTest {
    private val ethereum = ethereum()
    private val signService = ERC1271SignService(readOnlyTransactionSender())

    private val signature: Binary = run {
        val privateKey = Numeric.toBigInt(RandomUtils.nextBytes(32))
        Word.apply(ByteArray(32)).sign(privateKey)
    }

    @Test
    fun `should verify signature with UTF-8`() = runBlocking<Unit> {
        val result = signService.isSigner(
            Address.apply("0x76c5855e93bd498b6331652854c4549d34bc3a30"),
            "11Ằ☭:fox_face::mouse:ößt 桜どラ ❉",
            Binary.apply("0x1917e545f491815865abaaaeba5fb115160376f5110c1c7a125702dd45f4430d3c2653c437abc794c5d68dfe486c7d5524f77bced85bbf544efdb8260d7e89db1c")
        )
        assertThat(result).isTrue()
    }

    @Test
    internal fun `should verify signature with v greater than 30`() = runBlocking<Unit> {
        fun ethSign(message: ByteArray, privateKey: BigInteger): Sign.SignatureData {
            val prefixedMessage = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray() + message
            val publicKey = Sign.publicKeyFromPrivate(privateKey)
            return Sign.signMessage(prefixedMessage, publicKey, privateKey)
        }

        val (privateKey, _, address) = generateNewKeys()

        // Some arbitrarily encoded data. In real life this may be EIP-712 encoding of the 'Order' struct.
        val data = Binary(RandomUtils.nextBytes(333))
        val dataHash = keccak256(data)

        val signedHash = ethSign(dataHash.bytes(), privateKey)
        val v30Signature = Sign.SignatureData((signedHash.v + 4).toByte(), signedHash.r, signedHash.s).toBinary()

        assertThat(signService.isSigner(address, dataHash, v30Signature)).isTrue()
    }

    @Test
    fun `should verify signature`() = runBlocking<Unit> {
        val signer = signingTransactionSender()

        val contract = TestERC1271.deployAndWait(signer, monoTransactionPoller()).awaitFirst()
        contract
            .setReturnSuccessfulValidSignature(true)
            .execute().verifySuccess()

        val hash = ByteArray(32)
        assertThat(signService.isSigner(contract.address(), Word.apply(hash), signature))
            .isTrue()
        assertThat(signService.isSigner(contract.address(), Word.apply(hash), Binary.empty()))
            .isTrue()
    }

    @Test
    fun `should not verify signature`() = runBlocking<Unit> {
        val signer = signingTransactionSender()

        val contract = TestERC1271.deployAndWait(signer, monoTransactionPoller()).awaitFirst()
        contract
            .setReturnSuccessfulValidSignature(false)
            .execute().awaitFirst()

        val hash = ByteArray(32)
        val result = signService.isSigner(contract.address(), Word.apply(hash), signature)
        assertThat(result).isFalse()
    }

    private fun signingTransactionSender(): MonoSigningTransactionSender {
        val byteArray = ByteArray(32)
        ThreadLocalRandom.current().nextBytes(byteArray)
        val privateKey = Numeric.toBigInt(byteArray)

        return MonoSigningTransactionSender(
            ethereum,
            MonoSimpleNonceProvider(ethereum),
            privateKey,
            BigInteger.valueOf(8000000),
            MonoGasPriceProvider { Mono.just(BigInteger.ZERO) }
        )
    }

    private fun  monoTransactionPoller(): MonoTransactionPoller {
        return MonoTransactionPoller(ethereum)
    }

    private fun ethereumUrl(): URI = EthereumTestExtension.ethereumContainer.ethereumUrl()

    private fun readOnlyTransactionSender(from: Address = Address.ZERO()): ReadOnlyMonoTransactionSender {
        return ReadOnlyMonoTransactionSender(ethereum, from)
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

    private fun Mono<Word>.waitReceipt(): TransactionReceipt {
        val value = this.block()
        require(value != null) { "txHash is null" }
        return ethereum.ethGetTransactionReceipt(value).block()!!.get()
    }

    private fun Mono<Word>.verifySuccess(): TransactionReceipt {
        val receipt = waitReceipt()
        Assertions.assertTrue(receipt.success())
        return receipt
    }

    private fun Word.sign(privateKey: BigInteger): Binary {
        val publicKey = Sign.publicKeyFromPrivate(privateKey)
        return Sign.signMessageHash(bytes(), publicKey, privateKey).toBinary()
    }

    private fun Sign.SignatureData.toBinary(): Binary = Binary.apply(this.r).add(this.s).add(byteArrayOf(this.v))

}