package com.rarible.ethereum.listener.log;

import com.rarible.core.logging.LoggerContext;
import com.rarible.core.logging.LoggingUtils;
import com.rarible.ethereum.block.BlockEvent;
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
import kotlin.Pair;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rarible.core.apm.JavaHelpers.withSpan;
import static com.rarible.core.apm.JavaHelpers.withTransaction;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    private final long blockProcessingDelay;
    private final long blockListeningDelay;
    private final long stopListeningBlock;
    private final long batchSize;
    private final LogEventRepository logEventRepository;
    private final PendingLogService pendingLogService;
    private final List<OnLogEventListener> onLogEventListeners;

    public LogListenService(
        LogEventRepository logEventRepository,
        BlockRepository blockRepository,
        MonoEthereum ethereum,
        List<LogEventsListener> logEventsListeners,
        List<LogEventDescriptor<?>> descriptors,
        List<OnLogEventListener> onLogEventListeners,
        PendingLogService pendingLogService,
        BlockListenService<SimpleBlock> blockListenService,
        @Value("${ethereumBackoffMaxAttempts:5}") long maxAttempts,
        @Value("${ethereumBackoffMinBackoff:100}") long minBackoff,
        @Value("${ethereumMaxProcessTime:300000}") long maxProcessTime,
        @Value("${ethereumBlockBatchSize:100}") long batchSize,
        @Value("${ethereumBlockListeningDelay:300000}") long blockListeningDelay,
        @Value("${ethereumBlockProcessingDelay:0}") long blockProcessingDelay,
        @Value("${ethereumStopListeningBlock:9223372036854775807}") long stopListeningBlock
    ) {
        this.backoff = Retry.backoff(maxAttempts, Duration.ofMillis(minBackoff));
        this.maxProcessTime = maxProcessTime;
        this.blockProcessingDelay = blockProcessingDelay;
        this.blockListeningDelay = blockListeningDelay;
        this.stopListeningBlock = stopListeningBlock;
        this.blockRepository = blockRepository;
        this.ethereum = ethereum;
        this.logEventsListeners = logEventsListeners;
        this.blockListenService = blockListenService;
        this.batchSize = batchSize;
        this.logEventRepository = logEventRepository;
        this.pendingLogService = pendingLogService;
        this.onLogEventListeners = onLogEventListeners;

        logger.info("injected descriptors: {}", descriptors);

        this.listeners = descriptors.stream()
                .map(this::createLogEventListener)
                .collect(toList());

        this.listenersMap = listeners.stream()
                .collect(Collectors.toMap(it -> it.getDescriptor().getTopic(), it -> it));
    }

    public LogEventListener<?> getListenerByTopic(Word topic) {
        return listenersMap.get(topic);
    }

    @PostConstruct
    public void init() {
        Flux<BlockEvent<SimpleBlock>> blocks = Mono.delay(Duration.ofMillis(1000))
                .thenMany(blockListenService.listen());
        /*
         *  We delay processing of blocks for a while to give Ethereum nodes time to synchronize transactions' traces.
         *  This may not be enough, but at least minimizes number of block errors caused by unavailable traces.
         */
        (blockProcessingDelay != 0 ? blocks.delayElements(Duration.ofMillis(blockProcessingDelay)) : blocks)
            .map(it -> new NewBlockEvent(it.getBlock().getNumber(), it.getBlock().getHash(), it.getBlock().getTimestamp(), it.getReverted() != null ? Word.apply(it.getReverted().getHash()) : null))
            .timeout(Duration.ofMillis(blockListeningDelay))
            .concatMap(it -> {
                if (it.getNumber() >= stopListeningBlock) {
                    logger.info("New block {} is greater or equal then stop block {}, skip handling", it.getNumber(), stopListeningBlock);
                    return Mono.empty();
                } else  {
                    return this.onBlockEvents(singletonList(it));
                }
            })
            .then(Mono.<Void>error(new IllegalStateException("disconnected")))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(300))
                    .maxBackoff(Duration.ofMillis(2000))
                    .doAfterRetry(s -> logger.warn("retrying connection to the node (attempt = {})", s))
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

    public Flux<LongRange> reindexWithDescriptor(LogEventDescriptor<?> descriptor, long from, long to) {
        final LogEventListener<?> listener = createLogEventListener(descriptor);
        return listener.reindex(from, to);
    }

    public Mono<Void> reindexBlocks(List<BlockHead> blocks) {
        return LoggingUtils.withMarker(marker -> {
            logger.info(marker, "reindexing blocks {}", blocks);
            return Flux.merge(blocks.stream()
                            .map(it -> ethereum.ethGetBlockByNumber(BigInteger.valueOf(it.getId())))
                            .collect(toList()))
                    .collectList()
                    .map(list -> list.stream().map(it -> new NewBlockEvent(it.number().longValue(), it.hash(), it.timestamp().longValue(), null)).collect(toList()))
                    .flatMap(this::onBlockEvents);
        });
    }

    public Mono<Void> onBlockEvents(List<NewBlockEvent> events) {
        final Mono<Void> result = LoggingUtils.withMarker(marker -> {
            logger.info(marker, "onBlockEvents {}", events);
            return withSpan(
                Flux.merge(events.stream().map(this::onBlockEvent).collect(toList())).collectList(),
                "processLogs", null, null, null, emptyList()
            )
                .flatMap(it -> postProcessLogs(it).thenReturn(BlockStatus.SUCCESS))
                .timeout(Duration.ofMillis(maxProcessTime))
                .onErrorResume(ex -> {
                    logger.error(marker, "Unable to handle events " + events, ex);
                    return Mono.just(BlockStatus.ERROR);
                })
                .flatMap(status ->
                        Flux.merge(events.stream().map(it -> blockRepository.updateBlockStatus(it.getNumber(), status)).collect(toList())).then()
                )
                .then()
                .onErrorResume(ex -> {
                    logger.error(marker, "Unable to save block status " + events, ex);
                    return Mono.empty();
                });
        });
	    if (events.size() == 1) {
		    final NewBlockEvent event = events.get(0);
		    return withTransaction(
			    result.contextWrite(ctx -> LoggerContext.addToContext(ctx, event.getContextParams())),
			    "block",
			    asList(
				    new Pair<>("blockNumber", event.getNumber()),
				    new Pair<>("blockHash", event.getHash().toString())
			    ),
			    null,
			    null
		    );
	    } else {
		    final List<Long> numbers = events.stream().map(NewBlockEvent::getNumber).collect(toList());
		    return withTransaction(
			    result.contextWrite(ctx -> ctx.put("blockNumbers", numbers.toString())),
			    "block",
			    singletonList(new Pair<>("blockNumbers", numbers.toString())),
			    null,
			    null
		    );
	    }
    }

    private Flux<LogEvent> onBlockEvent(NewBlockEvent event) {
        return withSpan(
                ethereum.ethGetFullBlockByHash(event.getHash()),
                "getFullBlock", null, null, null,
                Collections.emptyList()
        )
        .doOnError(throwable -> logger.warn("Unable to get block by hash: " + event.getHash(), throwable))
        .retryWhen(backoff)
        .flatMapMany(block -> Flux.fromIterable(listeners).flatMap(listener -> listener.onBlockEvent(event, block)));
    }

    private Mono<Void> postProcessLogs(List<LogEvent> logs) {
        final Mono<Void> result = Flux.fromIterable(logEventsListeners != null ? logEventsListeners : emptyList())
            .flatMap(it -> it.postProcessLogs(logs))
            .then();
        return withSpan(result, "postProcess", null, null, null, emptyList());
    }

    private LogEventListener<?> createLogEventListener(LogEventDescriptor<?> descriptor) {
        final List<OnLogEventListener> topicOnEventListeners = onLogEventListeners.stream()
                .filter(onEventListener -> onEventListener.getTopics().contains(descriptor.getTopic()) )
                .collect(toList());

        return new LogEventListener<>(
                descriptor,
                topicOnEventListeners,
                pendingLogService,
                logEventRepository,
                ethereum,
                backoff,
                batchSize
        );
    }
}
