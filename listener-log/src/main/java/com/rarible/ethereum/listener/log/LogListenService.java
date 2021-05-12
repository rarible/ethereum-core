package com.rarible.ethereum.listener.log;

import com.rarible.core.logging.LoggerContext;
import com.rarible.core.logging.LoggingUtils;
import com.rarible.ethereum.block.BlockListenService;
import com.rarible.ethereum.listener.log.block.SimpleBlock;
import com.rarible.ethereum.listener.log.domain.BlockHead;
import com.rarible.ethereum.listener.log.domain.BlockStatus;
import com.rarible.ethereum.listener.log.domain.LogEvent;
import com.rarible.ethereum.listener.log.domain.NewBlockEvent;
import com.rarible.ethereum.listener.log.persist.BlockRepository;
import com.rarible.ethereum.listener.log.persist.LogEventRepository;
import com.rarible.ethereum.log.LogEventsListener;
import io.daonomic.rpc.domain.Word;
import kotlin.ranges.LongRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import scalether.core.MonoEthereum;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Component
public class LogListenService {
    private static final Logger logger = LoggerFactory.getLogger(LogListenService.class);

    private final BlockRepository blockRepository;
    private final MonoEthereum ethereum;
    private final List<LogEventsListener> logEventsListeners;
    private final BlockListenService<SimpleBlock> blockListenService;
    private final RetryBackoffSpec backoff;
    private final List<LogEventListener<?>> listeners;
    private final Map<Word, LogEventListener<?>> listenersMap;
    private final long maxProcessTime;

    public LogListenService(
        LogEventRepository logEventRepository,
        BlockRepository blockRepository,
        MonoEthereum ethereum,
        List<LogEventsListener> logEventsListeners,
        List<LogEventDescriptor<?>> descriptors,
        PendingLogService pendingLogService,
        BlockListenService<SimpleBlock> blockListenService,
        @Value("${ethereumBackoffMaxAttempts:5}") long maxAttempts,
        @Value("${ethereumBackoffMinBackoff:100}") long minBackoff,
        @Value("${ethereumMaxProcessTime:300000}") long maxProcessTime,
        @Value("${ethereumBlockBatchSize:100}") long batchSize
    ) {
        this.backoff = Retry.backoff(maxAttempts, Duration.ofMillis(minBackoff));
        this.maxProcessTime = maxProcessTime;
        this.blockRepository = blockRepository;
        this.ethereum = ethereum;
        this.logEventsListeners = logEventsListeners;
        this.blockListenService = blockListenService;
        logger.info("injected descriptors: {}", descriptors);
        this.listeners = descriptors.stream()
            .map(it -> new LogEventListener<>(it, pendingLogService, logEventRepository, ethereum, backoff, batchSize))
            .collect(toList());
        this.listenersMap = listeners.stream()
            .collect(Collectors.toMap(it -> it.getDescriptor().getTopic(), it -> it));
    }

    public LogEventListener<?> getListenerByTopic(Word topic) {
        return listenersMap.get(topic);
    }

    @PostConstruct
    public void init() {
        Mono.delay(Duration.ofMillis(1000))
            .thenMany(blockListenService.listen())
            .map(it -> new NewBlockEvent(it.getBlock().getNumber(), it.getBlock().getHash(), it.getBlock().getTimestamp(), it.getReverted() != null ? Word.apply(it.getReverted().getHash()) : null))
            .timeout(Duration.ofMinutes(5))
            .concatMap(this::onBlock)
            .then(Mono.<Void>error(new IllegalStateException("disconnected")))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(300))
                    .maxBackoff(Duration.ofMillis(2000))
                    .doAfterRetry(s -> logger.warn("retrying {}", s))
            )
            .subscribe(
                n -> {
                },
                th -> logger.error("unable to process block events. should never happen", th)
            );
    }

    public Flux<LongRange> reindex(Word topic, long from, long to) {
        final LogEventListener<?> listener = getListenerByTopic(topic);
        return listener.reindex(from, to);
    }

    public Mono<Void> reindexBlock(BlockHead block) {
        return LoggingUtils.withMarker(marker -> {
            logger.info(marker, "reindexing block {}", block);
            return ethereum.ethGetBlockByNumber(BigInteger.valueOf(block.getId()))
                .flatMap(it -> onBlock(new NewBlockEvent(block.getId(), it.hash(), it.timestamp().longValue(), null)));
        });
    }

    public Mono<Void> onBlock(NewBlockEvent event) {
        return LoggingUtils.withMarker(marker -> {
            logger.info(marker, "onBlockEvent {}", event);
            return onBlockEvent(event)
                .collectList()
                .flatMap(it -> postProcessLogs(it).thenReturn(BlockStatus.SUCCESS))
                .timeout(Duration.ofMillis(maxProcessTime))
                .onErrorResume(ex -> {
                    logger.error(marker, "Unable to handle event " + event, ex);
                    return Mono.just(BlockStatus.ERROR);
                })
                .flatMap(status -> blockRepository.updateBlockStatus(event.getNumber(), status))
                .then()
                .onErrorResume(ex -> {
                    logger.error(marker, "Unable to save block status " + event, ex);
                    return Mono.empty();
                });
        })
            .subscriberContext(ctx -> LoggerContext.addToContext(ctx, event.getContextParams()));
    }

    private Flux<LogEvent> onBlockEvent(NewBlockEvent event) {
        return Flux.fromIterable(listeners)
            .flatMap(it -> it.onBlockEvent(event));
    }

    private Mono<Void> postProcessLogs(List<LogEvent> logs) {
        return Flux.fromIterable(logEventsListeners != null ? logEventsListeners : emptyList())
            .flatMap(it -> it.postProcessLogs(logs))
            .then();
    }
}
