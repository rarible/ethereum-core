package com.rarible.ethereum.sign.service

import com.rarible.contracts.erc1271.IERC1271
import com.rarible.ethereum.common.keccak256
import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.reactive.awaitFirst
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.web3jold.crypto.Keys
import org.web3jold.crypto.Sign
import scalether.domain.Address
import scalether.transaction.MonoTransactionSender
import scalether.util.Hash
import java.nio.charset.StandardCharsets

class ERC1271SignService(
    private val sender: MonoTransactionSender
) {
    @Throws(CheckSignatureException::class, InvalidSignatureException::class)
    suspend fun isSigner(signer: Address, message: String, signature: Binary): Boolean {
        val hash = Word(Hash.sha3(addStart(message).bytes()))
        logger.info("hash is $hash for messaage $message")
        return isSigner(signer, hash, signature)
    }

    @Throws(CheckSignatureException::class, InvalidSignatureException::class)
    suspend fun isSigner(signer: Address, hash: Word, signature: Binary): Boolean {
        if (signature.bytes().size == 65 && recover(hash, signature) == signer) {
            return true
        }

        val erc1271 = IERC1271(signer, sender)

        return try {
            logger.info("Calling isValidSignature using signer $signer hash is $hash signature is $signature")
            val result = erc1271.isValidSignature(hash.bytes(), signature.bytes()).call().awaitFirst()
            Binary.apply(result) == MAGIC_VALUE
        } catch (ex: RpcCodeException) {
            logger.error("Error calling isValidSignature", ex)
            false
        } catch (ex: IllegalArgumentException) {
            logger.error("Error calling isValidSignature", ex)
            false
        } catch (ex: Exception) {
            throw CheckSignatureException("Can't get method call result", ex)
        }
    }

    @Throws(InvalidSignatureException::class)
    fun recover(hash: Word, signature: Binary): Address {
        if (signature.bytes().size != 65) {
            throw InvalidSignatureException(
                "Invalid signature [${signature.prefixed()}] size ${signature.bytes().size}, should be 65 bytes"
            )
        }
        return try {
            val (v, h) = fixVAndHash(signature.bytes()[64], hash)
            val r = ByteArray(32)
            val s = ByteArray(32)
            System.arraycopy(signature.bytes(), 0, r, 0, 32)
            System.arraycopy(signature.bytes(), 32, s, 0, 32)
            val publicKey = Sign.signedMessageHashToKey(h.bytes(), Sign.SignatureData(v, r, s))
            Address.apply(Keys.getAddress(publicKey))
        } catch (ex: Exception) {
            throw InvalidSignatureException(
                "Invalid structure of signature [${signature.prefixed()}] - ${ex.message}", ex
            )
        }
    }

    /**
     * Returns an Ethereum Signed Message, created from a `hash`. This replicates the behavior of the
     * https://github.com/ethereum/wiki/wiki/JSON-RPC#eth_sign JSON-RPC method applied to message hash.
     */
    private fun getEthSignedMessageHash(hash: Word): Word =
        keccak256(Binary.apply("${START}32".toByteArray()).add(hash))

    private fun fixVAndHash(v: Byte, hash: Word): Pair<Byte, Word> = when(v.toInt()) {
        0, 1 -> (27 + v).toByte() to hash
        27, 28 -> v to hash
        //For hardware wallets that do not support EIP-712 we artificially increment v by 4 to distinguish signing of message's hash by 'eth_sign'.
        31, 32 -> (v - 4).toByte() to getEthSignedMessageHash(hash)
        else -> error("Value of 'v' is not recognised: $v")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ERC1271SignService::class.java)
        val MAGIC_VALUE: Binary = Binary.apply("0x1626ba7e")
        private const val START = "\u0019Ethereum Signed Message:\n"

        fun addStart(message: String): Binary {
            val resultMessage = START + message.toByteArray(StandardCharsets.UTF_8).size + message
            return Binary.apply(resultMessage.toByteArray(StandardCharsets.UTF_8))
        }
    }
}