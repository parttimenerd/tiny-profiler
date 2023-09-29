package me.bechberger;

import java.time.Duration;

/**
 * The sampling profiler
 */
public class Profiler implements Runnable {
    private volatile boolean stop = false;
    private final Options options;
    private final Store store;

    private Profiler(Options options) {
        this.options = options;
        this.store = new Store(options.getFlamePath());
    }

    public static Profiler newInstance(Options options) {
        Profiler profiler = new Profiler(options);
        Runtime.getRuntime().addShutdownHook(new Thread(profiler::onEnd));
        return profiler;
    }

    private static void sleep(Duration duration) throws InterruptedException {
        if (duration.isNegative() || duration.isZero()) {
            return;
        }
        Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1000000);
    }

    @Override
    public void run() {
        while (!stop) {
            Duration start = Duration.ofNanos(System.nanoTime());
            sample();
            Duration duration = Duration.ofNanos(System.nanoTime()).minus(start);
            Duration sleep = options.getInterval().minus(duration);
            try {
                sleep(sleep);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (options.printMethodTable()) {
            store.printMethodTable();
        }
        store.storeFlameGraphIfNeeded();
        stop = false;
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
        stop = true;
        while (stop);
    }
}