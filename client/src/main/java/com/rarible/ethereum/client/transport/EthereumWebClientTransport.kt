package com.rarible.ethereum.client.transport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.ScalaObjectMapper
import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.TcpClient
import scala.reflect.Manifest
import java.util.concurrent.TimeUnit

open class EthereumWebClientTransport(
    private val rpcUrl: String,
    private val mapper: ObjectMapper,
    private val requestTimeoutMs: Int = 10000,
    private val readWriteTimeoutMs: Int = 10000,
    private val mediaType: MediaType = MediaType.APPLICATION_JSON,
) : WebClientTransport(null, null, 0, 0) {
    val client = buildWebClient()

    override fun buildClient(): WebClient {
        return WebClient.create()
    }

    protected open fun buildWebClient(): WebClient {
        val tcpClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, requestTimeoutMs)
            .doOnConnected { conn ->
                conn
                    .addHandlerLast(ReadTimeoutHandler(readWriteTimeoutMs.toLong(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(readWriteTimeoutMs.toLong(), TimeUnit.MILLISECONDS))
            }
        val connector = ReactorClientHttpConnector(HttpClient.from(tcpClient))
        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(maxInMemorySize())
                configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper, mediaType))
                configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper, mediaType))
            }
            .build()

        val builder = WebClient.builder()
            .baseUrl(rpcUrl)
            .clientConnector(connector)
            .exchangeStrategies(exchangeStrategies)
        headers().foreach {
            builder.defaultHeader(it._1, it._2)
        }
        return builder.build()
    }

    override fun <T : Any?> get(url: String?, `evidence$1`: Manifest<T>): Mono<T> {
        return client.get()
            .uri(url)
            .retrieve()
            .bodyToMono(ParameterizedTypeReference.forType(`evidence$1`.runtimeClass()))
    }

    override fun <T : Any?> send(request: Request?, `evidence$1`: Manifest<T>): Mono<Response<T>> {
        val parameterType = (mapper as ScalaObjectMapper).constructType(`evidence$1`)
        val type = mapper.typeFactory.constructParametricType(Response::class.java, parameterType)
        return client.post()
            .body(BodyInserters.fromObject(request))
            .retrieve()
            .bodyToMono(ParameterizedTypeReference.forType(type))
    }
}
