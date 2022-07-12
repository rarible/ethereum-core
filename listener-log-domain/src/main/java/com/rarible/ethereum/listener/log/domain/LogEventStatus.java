package com.rarible.ethereum.listener.log.domain;

public enum LogEventStatus {
    @Deprecated
    PENDING,
    CONFIRMED,
    REVERTED,
    @Deprecated
    DROPPED,
    @Deprecated
    INACTIVE
}
