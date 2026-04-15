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
package io.micronaut.configuration.mongo.core

import spock.lang.Specification

import java.time.Duration

class MongoClientCloserSpec extends Specification {

    void "graceful shutdown waits for the longest tracked delay"() {
        given:
        MongoClientCloser mongoClientCloser = new MongoClientCloser()
        Object shortDelayClient = new Object()
        Object longDelayClient = new Object()
        mongoClientCloser.track(shortDelayClient, Duration.ofMillis(100))
        mongoClientCloser.track(longDelayClient, Duration.ofMillis(200))

        when:
        long firstShutdownStarted = System.nanoTime()
        mongoClientCloser.shutdownGracefully().toCompletableFuture().join()
        long firstShutdownElapsed = System.nanoTime() - firstShutdownStarted

        and:
        mongoClientCloser.untrack(longDelayClient)

        long secondShutdownStarted = System.nanoTime()
        mongoClientCloser.shutdownGracefully().toCompletableFuture().join()
        long secondShutdownElapsed = System.nanoTime() - secondShutdownStarted

        and:
        mongoClientCloser.untrack(shortDelayClient)

        long finalShutdownStarted = System.nanoTime()
        mongoClientCloser.shutdownGracefully().toCompletableFuture().join()
        long finalShutdownElapsed = System.nanoTime() - finalShutdownStarted

        then:
        Duration.ofNanos(firstShutdownElapsed).toMillis() >= 150
        Duration.ofNanos(secondShutdownElapsed).toMillis() >= 80
        Duration.ofNanos(secondShutdownElapsed).toMillis() < 180
        Duration.ofNanos(finalShutdownElapsed).toMillis() < 80
    }
}
