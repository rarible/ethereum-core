package com.rarible.ethereum.sign.service

import com.rarible.contracts.erc1271.IERC1271
import io.daonomic.rpc.RpcCodeException
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.reactive.awaitFirst
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
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
        if (recover(hash, signature) == signer) {
            return true
        }

        val erc1271 = IERC1271(signer, sender)

        return try {
            logger.info("calling isValidSignature using signer $signer hash is $hash signature is $signature")
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
        require(signature.bytes().size == 65) {
            "Invalid signature size ${signature.bytes().size}, should be 65 bytes"
        }
        return try {
            val v = fixV(signature.bytes()[64])
            val r = ByteArray(32)
            val s = ByteArray(32)
            System.arraycopy(signature.bytes(), 0, r, 0, 32)
            System.arraycopy(signature.bytes(), 32, s, 0, 32)
            val publicKey = Sign.signedMessageHashToKey(hash.bytes(), Sign.SignatureData(v, r, s))
            Address.apply(Keys.getAddress(publicKey))
        } catch (ex: Exception) {
            throw InvalidSignatureException("Invalid signature structure", ex)
        }
    }

    private fun fixV(v: Byte): Byte {
        return if (v < 27) (27 + v).toByte() else v
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(ERC1271SignService::class.java)
        val MAGIC_VALUE: Binary = Binary.apply("0x1626ba7e")
        private const val START = "\u0019Ethereum Signed Message:\n"

        fun addStart(message: String): Binary {
            val resultMessage = START + message.toByteArray(StandardCharsets.UTF_8).size + message
            return Binary.apply(resultMessage.toByteArray(StandardCharsets.UTF_8))
        }
    }
}