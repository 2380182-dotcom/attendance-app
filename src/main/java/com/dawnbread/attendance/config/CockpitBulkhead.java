package com.dawnbread.attendance.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One shared limit for BOTH cockpits (Sales and HR): at most MAX_CONCURRENT
 * heavy cockpit computations run at once on the whole server (512 MB, a
 * 10-connection DB pool). Extra requests wait up to WAIT_SECONDS, then the
 * caller answers 503 "busy, retry" instead of piling up threads, memory and
 * pool connections. Cached answers return in milliseconds, so permits are
 * released quickly.
 */
@Component
public class CockpitBulkhead {

    public static final int MAX_CONCURRENT = 2;
    public static final int WAIT_SECONDS = 25;

    private final Semaphore permits = new Semaphore(MAX_CONCURRENT, true);

    /** Thrown when no permit became free in time; controllers turn it into 503 + Retry-After. */
    public static class BusyException extends RuntimeException {
        public BusyException() {
            super("The cockpit is busy — retrying in a few seconds.");
        }
    }

    public <T> T run(Supplier<T> work) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusyException();
            }
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusyException();
        } finally {
            if (acquired) permits.release();
        }
    }
}
