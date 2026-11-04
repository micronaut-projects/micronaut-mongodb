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
package io.micronaut.configuration.mongo.sync;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientCloser;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.env.DevelopmentInactive;

import jakarta.inject.Singleton;

import java.util.List;

/**
 * Builds the primary MongoClient.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = MongoClient.class)
@Requires(beans = DefaultMongoConfiguration.class)
@Factory
public class DefaultMongoClientFactory {

    private final MongoClientCloser mongoClientCloser;

    /**
     * @param mongoClientCloser Tracks configured shutdown delays
     */
    public DefaultMongoClientFactory(MongoClientCloser mongoClientCloser) {
        this.mongoClientCloser = mongoClientCloser;
    }

    /**
     * Factory method to return a client.
     * @param mongoConfiguration configuration pulled in
     * @param settings settings pulled in
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @DevelopmentInactive
    @Primary
    @Singleton
    protected MongoClient mongoClient(DefaultMongoConfiguration mongoConfiguration, MongoClientSettings settings) {
        return mongoClientCloser.track(
            MongoClients.create(settings),
            mongoConfiguration.getShutdownDelay()
        );
    }

    /**
     * The client of a generation in development mode, over a retained driver client. Declared by this factory, so
     * that a replacement of its client replaces this one too.
     *
     * @param configuration The default configuration
     * @param settings The settings, whose codec registry the generation applies
     * @param customizers The settings customizers
     * @param beanContext The context
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation
     */
    @Bean(preDestroy = "close")
    @DevelopmentActive
    @Primary
    @Singleton
    DevelopmentMongoClient developmentMongoClient(DefaultMongoConfiguration configuration,
                                                  MongoClientSettings settings,
                                                  List<MongoClientSettingsBuilderCustomizer> customizers,
                                                  BeanContext beanContext,
                                                  BeanDependencyResolver dependencies) {
        return DevelopmentMongoClientFactory.mongoClient(configuration, settings, customizers, beanContext, dependencies);
    }
}
