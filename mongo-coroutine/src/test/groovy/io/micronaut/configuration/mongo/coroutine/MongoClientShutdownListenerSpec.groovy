/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.configuration.mongo.coroutine

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient
import io.micronaut.configuration.mongo.core.MongoClientCloser
import io.micronaut.context.event.BeanDestroyedEvent
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

class MongoClientShutdownListenerSpec extends Specification {

    void "test coroutine client is untracked when bean is destroyed"() {
        given:
        MongoClient mongoClient = new MongoClient(Stub(ReactiveMongoClient))
        MongoClientCloser mongoClientCloser = new MongoClientCloser()
        MongoClientShutdownListener shutdownListener = new MongoClientShutdownListener(mongoClientCloser)
        mongoClientCloser.track(mongoClient, Duration.ofMillis(250))

        when:
        long trackedStart = System.nanoTime()
        mongoClientCloser.shutdownGracefully().toCompletableFuture().get(2, TimeUnit.SECONDS)
        long trackedDelayMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - trackedStart)

        shutdownListener.onDestroyed(Stub(BeanDestroyedEvent) {
            getBean() >> mongoClient
        })

        long untrackedStart = System.nanoTime()
        mongoClientCloser.shutdownGracefully().toCompletableFuture().get(1, TimeUnit.SECONDS)
        long untrackedDelayMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - untrackedStart)

        then:
        trackedDelayMillis >= 200
        untrackedDelayMillis < 100

        cleanup:
        mongoClient?.close()
    }
}
