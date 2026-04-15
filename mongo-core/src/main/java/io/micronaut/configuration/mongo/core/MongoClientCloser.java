/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.mongo.core;

import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tracks Mongo clients and delays bean destruction during Micronaut graceful shutdown.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Singleton
public final class MongoClientCloser implements GracefulShutdownCapable {

    private static final Logger LOG = LoggerFactory.getLogger(MongoClientCloser.class);

    private final Map<Object, Duration> shutdownDelays = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Track a client with its configured shutdown delay.
     *
     * @param client The client
     * @param shutdownDelay The shutdown delay
     * @param <T> The mongo client type
     * @return The client
     */
    public <T> T track(T client, Duration shutdownDelay) {
        if (client != null && shutdownDelay != null && !shutdownDelay.isNegative() && !shutdownDelay.isZero()) {
            shutdownDelays.put(client, shutdownDelay);
        }
        return client;
    }

    /**
     * Stop tracking a client once bean destruction completes.
     *
     * @param client The client
     */
    public void untrack(Object client) {
        if (client != null) {
            shutdownDelays.remove(client);
        }
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        Duration shutdownDelay = maxShutdownDelay();
        if (shutdownDelay.isZero()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(shutdownDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while delaying Mongo client shutdown", e);
            }
        });
    }

    private Duration maxShutdownDelay() {
        synchronized (shutdownDelays) {
            return shutdownDelays.values().stream()
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
        }
    }
}
