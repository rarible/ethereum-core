package com.rarible.ethereum.client.monitoring

import com.fasterxml.jackson.databind.JsonNode
import com.rarible.ethereum.client.DummyMonoRpcTransport
import io.daonomic.rpc.domain.Binary
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.domain.Word
import reactor.core.publisher.Mono
import scala.Option
import scala.collection.immutable.List
import scala.collection.immutable.Seq
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import scalether.domain.Address
import scalether.domain.request.LogFilter
import scalether.domain.response.Block
import scalether.domain.response.Log
import scalether.domain.response.Transaction
import scalether.domain.response.TransactionReceipt
import java.math.BigInteger

class MonitoredEthereum(
    private val delegate: MonoEthereum,
    private val monitoringCallback: MonitoringCallback,
) : MonoEthereum(DummyMonoRpcTransport()) {

    override fun executeRaw(request: Request?): Mono<Response<JsonNode>> {
        return onBlockchainCall("executeRaw") {
            delegate.executeRaw(request)
        }
    }

    override fun <T : Any?> execOption(method: String?, params: Seq<Any>?, mf: Manifest<T>?): Mono<Option<T>> {
        return onBlockchainCall("execOption") {
            delegate.execOption(method, params, mf)
        }
    }

    override fun <T : Any?> exec(method: String?, params: Seq<Any>?, mf: Manifest<T>?): Mono<T> {
        return onBlockchainCall("exec") {
            delegate.exec(method, params, mf)
        }
    }

    override fun ethGetTransactionReceipt(hash: Word?): Mono<Option<TransactionReceipt>> {
        return onBlockchainCall("ethGetTransactionReceipt") {
            delegate.ethGetTransactionReceipt(hash)
        }
    }

    override fun ethGetFullBlockByHash(hash: Word?): Mono<Block<Transaction>> {
        return onBlockchainCall("ethGetFullBlockByHash") {
            delegate.ethGetFullBlockByHash(hash)
        }
    }

    override fun ethGetTransactionCount(address: Address?, defaultBlockParameter: String?): Mono<BigInteger> {
        return onBlockchainCall("ethGetTransactionCount") {
            delegate.ethGetTransactionCount(address, defaultBlockParameter)
        }
    }

    override fun ethSendRawTransaction(transaction: Binary?): Mono<Word> {
        return onBlockchainCall("ethSendRawTransaction") {
            delegate.ethSendRawTransaction(transaction)
        }
    }

    override fun ethEstimateGas(
        transaction: scalether.domain.request.Transaction?,
        defaultBlockParameter: String?
    ): Mono<BigInteger> {
        return onBlockchainCall("ethEstimateGas") {
            delegate.ethEstimateGas(transaction, defaultBlockParameter)
        }
    }

    override fun ethGetTransactionByHash(hash: Word?): Mono<Option<Transaction>> {
        return onBlockchainCall("ethGetTransactionByHash") {
            delegate.ethGetTransactionByHash(hash)
        }
    }

    override fun ethGetBalance(address: Address?, defaultBlockParameter: String?): Mono<BigInteger> {
        return onBlockchainCall("ethGetBalance") {
            delegate.ethGetBalance(address, defaultBlockParameter)
        }
    }

    override fun ethGetFullBlockByNumber(number: BigInteger?): Mono<Block<Transaction>> {
        return onBlockchainCall("ethGetFullBlockByNumber") {
            delegate.ethGetFullBlockByNumber(number)
        }
    }

    override fun netPeerCount(): Mono<BigInteger> {
        return onBlockchainCall("netPeerCount") {
            delegate.netPeerCount()
        }
    }

    override fun ethGetCode(address: Address?, defaultBlockParameter: String?): Mono<Binary> {
        return onBlockchainCall("ethGetCode") {
            delegate.ethGetCode(address, defaultBlockParameter)
        }
    }

    override fun ethBlockNumber(): Mono<BigInteger> {
        return onBlockchainCall("ethBlockNumber") {
            delegate.ethBlockNumber()
        }
    }

    override fun ethGetBlockByNumber(number: BigInteger?): Mono<Block<Word>> {
        return onBlockchainCall("ethGetBlockByNumber") {
            delegate.ethGetBlockByNumber(number)
        }
    }

    override fun ethNewFilter(filter: LogFilter?): Mono<BigInteger> {
        return onBlockchainCall("ethNewFilter") {
            delegate.ethNewFilter(filter)
        }
    }

    override fun ethGasPrice(): Mono<BigInteger> {
        return onBlockchainCall("ethGasPrice") {
            delegate.ethGasPrice()
        }
    }

    override fun web3Sha3(data: String?): Mono<String> {
        return onBlockchainCall("web3Sha3") {
            delegate.web3Sha3(data)
        }
    }

    override fun ethSendTransaction(transaction: scalether.domain.request.Transaction?): Mono<Word> {
        return onBlockchainCall("ethSendTransaction") {
            delegate.ethSendTransaction(transaction)
        }
    }

    override fun ethCall(
        transaction: scalether.domain.request.Transaction?,
        defaultBlockParameter: String?
    ): Mono<Binary> {
        return onBlockchainCall("ethCall") {
            delegate.ethCall(transaction, defaultBlockParameter)
        }
    }

    override fun ethGetFilterChanges(id: BigInteger?): Mono<List<Log>> {
        return onBlockchainCall("ethGetFilterChanges") {
            delegate.ethGetFilterChanges(id)
        }
    }

    override fun netListening(): Mono<Any> {
        return onBlockchainCall("netListening") {
            delegate.netListening()
        }
    }

    override fun ethGetLogs(filter: LogFilter?): Mono<List<Log>> {
        return onBlockchainCall("ethGetLogs") {
            delegate.ethGetLogs(filter)
        }
    }

    override fun ethGetBlockByHash(hash: Word?): Mono<Block<Word>> {
        return onBlockchainCall("ethGetBlockByHash") {
            delegate.ethGetBlockByHash(hash)
        }
    }

    override fun web3ClientVersion(): Mono<String> {
        return onBlockchainCall("web3ClientVersion") {
            delegate.web3ClientVersion()
        }
    }

    override fun netVersion(): Mono<String> {
        return onBlockchainCall("netVersion") {
            delegate.netVersion()
        }
    }

    override fun ethGetFilterChangesJava(id: BigInteger?): Mono<MutableList<Log>> {
        return onBlockchainCall("ethGetFilterChangesJava") {
            delegate.ethGetFilterChangesJava(id)
        }
    }

    override fun ethGetLogsJava(filter: LogFilter?): Mono<MutableList<Log>> {
        return onBlockchainCall("ethGetLogsJava") {
            delegate.ethGetLogsJava(filter)
        }
    }

    private fun <T> onBlockchainCall(method: String, monoCall: () -> Mono<T>): Mono<T> {
        return monitoringCallback.onBlockchainCall(method, monoCall)
    }
}

interface MonitoringCallback {
    fun <T> onBlockchainCall(
        method: String,
        monoCall: () -> Mono<T>
    ): Mono<T>
}
