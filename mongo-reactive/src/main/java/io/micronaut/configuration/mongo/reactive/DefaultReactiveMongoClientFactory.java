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
package io.micronaut.configuration.mongo.reactive;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientCloser;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.env.DevelopmentInactive;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Factory for the default {@link MongoClient}. Creates the injectable {@link Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(beans = DefaultMongoConfiguration.class)
@Factory
public class DefaultReactiveMongoClientFactory {

    private final MongoClientCloser mongoClientCloser;

    /**
     * @param mongoClientCloser Tracks configured shutdown delays
     */
    public DefaultReactiveMongoClientFactory(MongoClientCloser mongoClientCloser) {
        this.mongoClientCloser = mongoClientCloser;
    }

    /**
     * Factory Method for creating a client.
     * @param mongoConfiguration mongoConfiguration
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @DevelopmentInactive
    @Refreshable(MongoSettings.PREFIX)
    @Primary
    MongoClient mongoClient(DefaultMongoConfiguration mongoConfiguration) {
        return mongoClientCloser.track(
            MongoClients.create(mongoConfiguration.buildSettings()),
            mongoConfiguration.getShutdownDelay()
        );
    }

    /**
     * The client of a generation in development mode, over a retained driver client. Declared by this factory, so
     * that a replacement of its client replaces this one too.
     *
     * @param configuration The default configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation
     */
    @Bean(preDestroy = "close")
    @DevelopmentActive
    @Primary
    @Singleton
    DevelopmentReactiveMongoClient developmentMongoClient(DefaultMongoConfiguration configuration,
                                                          List<MongoClientSettingsBuilderCustomizer> customizers,
                                                          BeanDependencyResolver dependencies) {
        return DevelopmentReactiveMongoClientFactory.mongoClient(configuration, customizers, dependencies);
    }
}
