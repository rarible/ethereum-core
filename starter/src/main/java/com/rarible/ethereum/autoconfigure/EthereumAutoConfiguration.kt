package com.rarible.ethereum.autoconfigure

import io.daonomic.rpc.domain.Request
import io.daonomic.rpc.domain.Response
import io.daonomic.rpc.mono.WebClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClientException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import scala.reflect.Manifest
import scalether.core.MonoEthereum
import java.io.IOException
import java.time.Duration

@Configuration
@EnableConfigurationProperties(EthereumProperties::class)
@ConditionalOnProperty(prefix = RARIBLE_ETHEREUM, name = ["httpUrl"], matchIfMissing = false)
@ConditionalOnClass(MonoEthereum::class, WebClientTransport::class)
class EthereumAutoConfiguration(
    private val ethereumProperties: EthereumProperties
) {

    @Bean
    @ConditionalOnMissingBean(MonoEthereum::class)
    fun ethereum() = with(ethereumProperties) {
        val retry = Retry
            .backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffDelay))
            .filter { it is WebClientException || it is IOException }
        val transport = object : WebClientTransport(
            httpUrl,
            MonoEthereum.mapper(),
            requestTimeoutMs,
            readWriteTimeoutMs
        ) {
            override fun maxInMemorySize(): Int = maxFrameSize
            override fun <T : Any?> get(url: String?, manifest: Manifest<T>?): Mono<T> =
                super.get(url, manifest).retryWhen(retry)
            override fun <T : Any?> send(request: Request?, manifest: Manifest<T>?): Mono<Response<T>> =
                super.send(request, manifest).retryWhen(retry)
        }
        MonoEthereum(transport)
    }
}
