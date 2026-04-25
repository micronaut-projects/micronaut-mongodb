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
package io.micronaut.configuration.mongo.coroutine;

import com.mongodb.kotlin.client.coroutine.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.configuration.mongo.core.MongoClientCloser;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Factory for named {@link MongoClient} instances.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Requires(classes = MongoClient.class)
@Factory
public class NamedCoroutineMongoClientFactory {

    private final MongoClientCloser mongoClientCloser;

    /**
     * @param mongoClientCloser Tracks configured shutdown delays
     */
    public NamedCoroutineMongoClientFactory(MongoClientCloser mongoClientCloser) {
        this.mongoClientCloser = mongoClientCloser;
    }

    /**
     * Factory method to return a named client.
     *
     * @param configuration The named Mongo configuration
     * @return The tracked client
     */
    @Bean(preDestroy = "close")
    @EachBean(NamedMongoConfiguration.class)
    @Singleton
    MongoClient mongoClient(NamedMongoConfiguration configuration) {
        MongoClient mongoClient = new MongoClient(MongoClients.create(configuration.buildSettings()));
        mongoClient.appendMetadata(DefaultCoroutineMongoClientFactory.DRIVER_INFORMATION);
        return mongoClientCloser.track(mongoClient, configuration.getShutdownDelay());
    }
}
