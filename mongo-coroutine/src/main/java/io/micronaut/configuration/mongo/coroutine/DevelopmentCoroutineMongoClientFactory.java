/*
 * Copyright 2017-2026 original authors
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

import com.mongodb.MongoClientSettings;
import com.mongodb.kotlin.client.coroutine.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.configuration.mongo.core.dev.DevelopmentMongoSettings;
import io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry;
import io.micronaut.configuration.mongo.core.dev.GenerationMongoClient;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Creates the reactive driver clients under the coroutine {@link MongoClient}s that development mode retains, the
 * driver clients of each generation over them, and the coroutine clients of each generation for the development
 * methods of {@link DefaultCoroutineMongoClientFactory} and {@link NamedCoroutineMongoClientFactory}, which declare
 * them so that a replacement of the client of a factory replaces it in development mode too.
 *
 * <p>The coroutine client is a final wrapper over a reactive driver client. The driver client, its connection pools
 * and its monitors, is a {@link RetainedCoroutineMongoClient}, which development mode retains across a restart until
 * a change under {@code mongodb} releases it. The coroutine client of each generation wraps a
 * {@link DevelopmentCoroutineDriverClient}, a bean of its own type, which runs the operations on the retained client
 * with the codecs of that generation; it is the {@link GenerationMongoClient} the development reloader recreates when
 * a class its codecs may be built from changes in place, and the coroutine client, which received it, is recreated
 * with it. A client that command listeners, connection pool listeners or settings customizers apply to is not
 * retained, but created by each generation, as outside development mode. No shutdown delay applies in development
 * mode.</p>
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Factory
@Internal
@DevelopmentActive
@Requires(classes = MongoClient.class)
final class DevelopmentCoroutineMongoClientFactory {

    /**
     * @param configuration The default configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @Primary
    @Requires(beans = DefaultMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedCoroutineMongoClient retainedMongoClient(DefaultMongoConfiguration configuration) {
        return retain(configuration);
    }

    /**
     * @param configuration A named configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @EachBean(NamedMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedCoroutineMongoClient namedRetainedMongoClient(NamedMongoConfiguration configuration) {
        return retain(configuration);
    }

    /**
     * @param configuration The default configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The driver client of the generation, which the coroutine client wraps
     */
    @Singleton
    @Primary
    @Requires(beans = DefaultMongoConfiguration.class)
    @Bean(preDestroy = "close", typed = {DevelopmentCoroutineDriverClient.class, GenerationMongoClient.class})
    DevelopmentCoroutineDriverClient driverClient(DefaultMongoConfiguration configuration,
                                                  List<MongoClientSettingsBuilderCustomizer> customizers,
                                                  BeanDependencyResolver dependencies) {
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers)) {
            RetainedCoroutineMongoClient retained = dependencies.getBean(RetainedCoroutineMongoClient.class);
            return DevelopmentCoroutineDriverClient.over(retained.client(), DevelopmentMongoSettings.codecRegistry(configuration));
        }
        return own(configuration);
    }

    /**
     * @param configuration A named configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The driver client of the generation, which the coroutine client wraps
     */
    @Singleton
    @EachBean(NamedMongoConfiguration.class)
    @Bean(preDestroy = "close", typed = {DevelopmentCoroutineDriverClient.class, GenerationMongoClient.class})
    DevelopmentCoroutineDriverClient namedDriverClient(NamedMongoConfiguration configuration,
                                                       List<MongoClientSettingsBuilderCustomizer> customizers,
                                                       BeanDependencyResolver dependencies) {
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers)) {
            RetainedCoroutineMongoClient retained = dependencies.getBean(RetainedCoroutineMongoClient.class, Qualifiers.byName(configuration.getServerName()));
            return DevelopmentCoroutineDriverClient.over(retained.client(), DevelopmentMongoSettings.codecRegistry(configuration));
        }
        return own(configuration);
    }

    /**
     * @param dependencies Resolves the driver client of the generation, as a dependency of this one
     * @return The coroutine client of the generation, for the factory that declares it
     */
    static MongoClient mongoClient(BeanDependencyResolver dependencies) {
        return new MongoClient(dependencies.getBean(DevelopmentCoroutineDriverClient.class));
    }

    /**
     * @param configuration A named configuration
     * @param dependencies Resolves the driver client of the generation, as a dependency of this one
     * @return The coroutine client of the generation, for the factory that declares it
     */
    static MongoClient namedMongoClient(NamedMongoConfiguration configuration, BeanDependencyResolver dependencies) {
        return new MongoClient(dependencies.getBean(DevelopmentCoroutineDriverClient.class, Qualifiers.byName(configuration.getServerName())));
    }

    private static RetainedCoroutineMongoClient retain(AbstractMongoConfiguration configuration) {
        // the driver information is appended once, by the client that keeps it, not by each generation
        return new RetainedCoroutineMongoClient(MongoClients.create(
            DevelopmentMongoSettings.retainedSettings(configuration),
            DefaultCoroutineMongoClientFactory.DRIVER_INFORMATION
        ));
    }

    private static DevelopmentCoroutineDriverClient own(AbstractMongoConfiguration configuration) {
        MongoClientSettings settings = configuration.buildSettings();
        GenerationCodecRegistry codecRegistry = new GenerationCodecRegistry(settings.getCodecRegistry());
        return DevelopmentCoroutineDriverClient.owning(
            MongoClients.create(DevelopmentMongoSettings.ownedSettings(settings, codecRegistry), DefaultCoroutineMongoClientFactory.DRIVER_INFORMATION),
            codecRegistry
        );
    }
}
