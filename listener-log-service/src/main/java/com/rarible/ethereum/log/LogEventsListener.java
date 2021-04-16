package com.rarible.ethereum.log;

import com.rarible.ethereum.listener.log.domain.LogEvent;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LogEventsListener {
    Mono<Void> postProcessLogs(List<LogEvent> logs);
}
