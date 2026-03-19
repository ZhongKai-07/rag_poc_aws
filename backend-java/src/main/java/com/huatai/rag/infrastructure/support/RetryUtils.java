package com.huatai.rag.infrastructure.support;

import java.time.Duration;
import java.util.function.Supplier;

public class RetryUtils {

    public <T> T executeWithRetry(Supplier<T> action, int maxAttempts, Duration backoff) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt == maxAttempts) {
                    break;
                }
                sleep(backoff);
            }
        }
        throw lastException == null ? new IllegalStateException("Retry action failed") : lastException;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", exception);
        }
    }
}
