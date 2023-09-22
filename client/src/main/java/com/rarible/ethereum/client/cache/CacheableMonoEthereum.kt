package com.rarible.ethereum.client.cache

import com.fasterxml.jackson.databind.JsonNode
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import java.time.Duration

class CacheableMonoEthereum(
    private val delegate: MonoEthereum,
    expireAfter: Duration,
    cacheMaxSize: Long,
    blockByNumberCacheExpireAfter: Duration,
    private val enableCacheByNumber: Boolean
) : MonoEthereum(DummyMonoRpcTransport()) {

    private val blockByNumberCache: Cache<BigInteger, Block<Transaction>> = Caffeine.newBuilder()
        .expireAfterWrite(blockByNumberCacheExpireAfter)
        .maximumSize(cacheMaxSize)
        .build()

    private val blockByHashCache: AsyncLoadingCache<Word, Block<Transaction>> = Caffeine.newBuilder()
        .expireAfterWrite(expireAfter)
        .maximumSize(cacheMaxSize)
        .buildAsync { key, _ ->
            super
                .ethGetFullBlockByHash(key)
                .map {
                    blockByNumberCache.put(it.blockNumber, it)
                    it
                }
                .toFuture()
        }

    override fun ethGetFullBlockByHash(hash: Word): Mono<Block<Transaction>> {
        return Mono.fromFuture(blockByHashCache.get(hash))
    }

    override fun ethGetFullBlockByNumber(number: BigInteger): Mono<Block<Transaction>> {
        if (enableCacheByNumber) {
            val cache = blockByNumberCache.getIfPresent(number)
            if (cache != null) return Mono.just(cache)
        }
        return delegate
            .ethGetFullBlockByNumber(number)
            .flatMap { block -> Mono.fromFuture(blockByHashCache.get(block.hash()) { _ -> block }) }
    }

    override fun executeRaw(request: Request?): Mono<Response<JsonNode>> {
        return delegate.executeRaw(request)
    }

    override fun <T : Any?> execOption(method: String?, params: Seq<Any>?, mf: Manifest<T>?): Mono<Option<T>> {
        return delegate.execOption(method, params, mf)
    }

    override fun <T : Any?> exec(method: String?, params: Seq<Any>?, mf: Manifest<T>?): Mono<T> {
        return delegate.exec(method, params, mf)
    }

    override fun ethGetTransactionReceipt(hash: Word?): Mono<Option<TransactionReceipt>> {
        return delegate.ethGetTransactionReceipt(hash)
    }

    override fun ethGetTransactionCount(address: Address?, defaultBlockParameter: String?): Mono<BigInteger> {
        return delegate.ethGetTransactionCount(address, defaultBlockParameter)
    }

    override fun ethSendRawTransaction(transaction: Binary?): Mono<Word> {
        return delegate.ethSendRawTransaction(transaction)
    }

    override fun ethEstimateGas(
        transaction: scalether.domain.request.Transaction?,
        defaultBlockParameter: String?
    ): Mono<BigInteger> {
        return delegate.ethEstimateGas(transaction, defaultBlockParameter)
    }

    override fun ethGetTransactionByHash(hash: Word?): Mono<Option<Transaction>> {
        return delegate.ethGetTransactionByHash(hash)
    }

    override fun ethGetBalance(address: Address?, defaultBlockParameter: String?): Mono<BigInteger> {
        return delegate.ethGetBalance(address, defaultBlockParameter)
    }

    override fun netPeerCount(): Mono<BigInteger> {
        return delegate.netPeerCount()
    }

    override fun ethGetCode(address: Address?, defaultBlockParameter: String?): Mono<Binary> {
        return delegate.ethGetCode(address, defaultBlockParameter)
    }

    override fun ethBlockNumber(): Mono<BigInteger> {
        return delegate.ethBlockNumber()
    }

    override fun ethGetBlockByNumber(number: BigInteger?): Mono<Block<Word>> {
        return delegate.ethGetBlockByNumber(number)
    }

    override fun ethNewFilter(filter: LogFilter?): Mono<BigInteger> {
        return delegate.ethNewFilter(filter)
    }

    override fun ethGasPrice(): Mono<BigInteger> {
        return delegate.ethGasPrice()
    }

    override fun web3Sha3(data: String?): Mono<String> {
        return delegate.web3Sha3(data)
    }

    override fun ethSendTransaction(transaction: scalether.domain.request.Transaction?): Mono<Word> {
        return delegate.ethSendTransaction(transaction)
    }

    override fun ethCall(
        transaction: scalether.domain.request.Transaction?,
        defaultBlockParameter: String?
    ): Mono<Binary> {
        return delegate.ethCall(transaction, defaultBlockParameter)
    }

    override fun ethGetFilterChanges(id: BigInteger?): Mono<List<Log>> {
        return delegate.ethGetFilterChanges(id)
    }

    override fun netListening(): Mono<Any> {
        return delegate.netListening()
    }

    override fun ethGetLogs(filter: LogFilter?): Mono<List<Log>> {
        return delegate.ethGetLogs(filter)
    }

    override fun ethGetBlockByHash(hash: Word?): Mono<Block<Word>> {
        return delegate.ethGetBlockByHash(hash)
    }

    override fun web3ClientVersion(): Mono<String> {
        return delegate.web3ClientVersion()
    }

    override fun netVersion(): Mono<String> {
        return delegate.netVersion()
    }

    override fun ethGetFilterChangesJava(id: BigInteger?): Mono<MutableList<Log>> {
        return delegate.ethGetFilterChangesJava(id)
    }

    override fun ethGetLogsJava(filter: LogFilter?): Mono<MutableList<Log>> {
        return delegate.ethGetLogsJava(filter)
    }
}
