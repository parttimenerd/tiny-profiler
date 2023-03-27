package me.bechberger;

import java.time.Duration;

/**
 * The sampling profiler
 */
public class Profiler implements Runnable {
    private final Options options;
    private final Store store;

    public Profiler(Options options) {
        this.options = options;
        this.store = new Store(options.getFlamePath());
        Runtime.getRuntime().addShutdownHook(new Thread(this::onEnd));
    }

    private static void sleep(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1000000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (true) {
            Duration start = Duration.ofNanos(System.nanoTime());
            sample();
            Duration duration = Duration.ofNanos(System.nanoTime()).minus(start);
            Duration sleep = options.getInterval().minus(duration);
            sleep(sleep);
        }
    }

    private void sample() {
        // We have to sample all threads here, as we're requesting a safe point when calling
        // one of the stack trace gathering methods
        // Calling Thread.getAllStackTraces() only obtains a safe point ones
        Thread.getAllStackTraces().forEach((thread, stackTraceElements) -> {
            if (!thread.isDaemon()) { // exclude daemon threads
                store.addSample(stackTraceElements);
            }
        });
    }

    private void onEnd() {
        if (options.printMethodTable()) {
            store.printMethodTable();
        }
        store.storeFlameGraphIfNeeded();
    }
}