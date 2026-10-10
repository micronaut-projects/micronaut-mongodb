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
package io.micronaut.configuration.mongo.reactive;

import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.configuration.mongo.core.dev.DevelopmentMongoSettings;
import io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Creates the reactive driver clients development mode retains, and the {@link MongoClient} beans of each generation
 * for the development methods of {@link DefaultReactiveMongoClientFactory} and
 * {@link NamedReactiveMongoClientFactory}, which declare them so that a replacement of the client of a factory replaces it in development mode too. The driver client, its connection pools and its monitors, is a
 * {@link RetainedReactiveMongoClient}, which development mode retains across a restart until a change under
 * {@code mongodb} releases it. The client bean of each generation runs the operations on it with the codecs of that
 * generation. A client that command listeners, connection pool listeners or settings customizers apply to is not
 * retained, but created by each generation, as outside development mode. No shutdown delay applies in development
 * mode.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Factory
@Internal
@DevelopmentActive
final class DevelopmentReactiveMongoClientFactory {

    /**
     * @param configuration The default configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @Requires(beans = DefaultMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedReactiveMongoClient retainedMongoClient(DefaultMongoConfiguration configuration) {
        return new RetainedReactiveMongoClient(MongoClients.create(DevelopmentMongoSettings.retainedSettings(configuration)));
    }

    /**
     * @param configuration A named configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @EachBean(NamedMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedReactiveMongoClient namedRetainedMongoClient(NamedMongoConfiguration configuration) {
        return new RetainedReactiveMongoClient(MongoClients.create(DevelopmentMongoSettings.retainedSettings(configuration)));
    }

    /**
     * @param configuration The default configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation, for the factory that declares it
     */
    static DevelopmentReactiveMongoClient mongoClient(DefaultMongoConfiguration configuration,
                                               List<MongoClientSettingsBuilderCustomizer> customizers,
                                               BeanDependencyResolver dependencies) {
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers)) {
            return DevelopmentReactiveMongoClient.over(dependencies.getBean(RetainedReactiveMongoClient.class).client(), DevelopmentMongoSettings.codecRegistry(configuration));
        }
        MongoClientSettings settings = configuration.buildSettings();
        GenerationCodecRegistry codecRegistry = new GenerationCodecRegistry(settings.getCodecRegistry());
        return DevelopmentReactiveMongoClient.owning(MongoClients.create(DevelopmentMongoSettings.ownedSettings(settings, codecRegistry)), codecRegistry);
    }

    /**
     * @param configuration A named configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation, for the factory that declares it
     */
    static DevelopmentReactiveMongoClient namedMongoClient(NamedMongoConfiguration configuration,
                                                    List<MongoClientSettingsBuilderCustomizer> customizers,
                                                    BeanDependencyResolver dependencies) {
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers)) {
            RetainedReactiveMongoClient retained = dependencies.getBean(RetainedReactiveMongoClient.class, Qualifiers.byName(configuration.getServerName()));
            return DevelopmentReactiveMongoClient.over(retained.client(), DevelopmentMongoSettings.codecRegistry(configuration));
        }
        MongoClientSettings settings = configuration.buildSettings();
        GenerationCodecRegistry codecRegistry = new GenerationCodecRegistry(settings.getCodecRegistry());
        return DevelopmentReactiveMongoClient.owning(MongoClients.create(DevelopmentMongoSettings.ownedSettings(settings, codecRegistry)), codecRegistry);
    }
}
