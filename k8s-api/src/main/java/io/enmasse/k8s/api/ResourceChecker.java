/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.k8s.api;

import io.enmasse.k8s.api.cache.CacheWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ResourceChecker<T> implements CacheWatcher<T>, Runnable {
    private static final Logger log = LoggerFactory.getLogger(ResourceChecker.class.getName());
    private final Watcher<T> watcher;
    private final Semaphore signal = new Semaphore(0);
    private final Duration recheckInterval;
    private volatile ResourceCache<T> resourceCache;
    private volatile boolean synced = false;
    private volatile boolean running = false;
    private Thread thread;

    public ResourceChecker(Watcher<T> watcher, Duration recheckInterval) {
        this.watcher = watcher;
        this.recheckInterval = recheckInterval;
    }

    public void start() {
        running = true;
        thread = new Thread(this);
        thread.setName("resource-checker");
        thread.setDaemon(true);
        thread.start();

    }

    @Override
    public void run() {
        while (running) {
            doWork();
        }
    }

    void doWork() {
        try {
            signal.tryAcquire(recheckInterval.toMillis(), TimeUnit.MILLISECONDS);
            // update even if we didn't acquire (RouteStatusCheck relies on this).
            if (synced) {
                signal.drainPermits();
                watcher.onUpdate(resourceCache.getItems());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            log.warn("ResourceChecker interrupted, stopping.");
        } catch (Exception e) {
            log.warn("Exception in checker task", e);
        }
    }

    public void stop() {
        try {
            running = false;
            thread.interrupt();
            thread.join();
        } catch (InterruptedException ignored) {
            log.warn("Interrupted while stopping", ignored);
        }
    }

    @Override
    public void onInit(ResourceCache<T> cache) {
        this.resourceCache = cache;
    }

    @Override
    public void onUpdate() {
        synced = true;
        signal.release();
    }
}
