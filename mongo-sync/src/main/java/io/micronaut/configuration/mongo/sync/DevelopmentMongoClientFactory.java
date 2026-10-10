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
package io.micronaut.configuration.mongo.sync;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micronaut.configuration.mongo.core.DefaultMongoClientSettingsFactory;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.configuration.mongo.core.dev.DevelopmentMongoSettings;
import io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Creates the driver clients development mode retains, and the {@link MongoClient} beans of each generation for
 * the development methods of {@link DefaultMongoClientFactory} and
 * {@link NamedMongoClientFactory}, which declare them so that a replacement of the client of a factory replaces it in development mode too. The driver client, its connection pools and its monitors, is a
 * {@link RetainedSyncMongoClient}, which development mode retains across a restart until a change under
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
final class DevelopmentMongoClientFactory {

    /**
     * @param configuration The default configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @Requires(beans = DefaultMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedSyncMongoClient retainedMongoClient(DefaultMongoConfiguration configuration) {
        return new RetainedSyncMongoClient(MongoClients.create(DevelopmentMongoSettings.retainedSettings(configuration)));
    }

    /**
     * @param configuration A named configuration, whose values the client copies
     * @return The retained client
     */
    @Singleton
    @EachBean(NamedMongoConfiguration.class)
    @Retain(invalidatedBy = MongoSettings.PREFIX)
    @Bean(preDestroy = "close")
    RetainedSyncMongoClient namedRetainedMongoClient(NamedMongoConfiguration configuration) {
        return new RetainedSyncMongoClient(MongoClients.create(DevelopmentMongoSettings.retainedSettings(configuration)));
    }

    /**
     * @param configuration The default configuration
     * @param settings The settings, whose codec registry the generation applies
     * @param customizers The settings customizers
     * @param beanContext The context, which tells whether the settings are those the configuration builds
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation, for the factory that declares it
     */
    static DevelopmentMongoClient mongoClient(DefaultMongoConfiguration configuration,
                                       MongoClientSettings settings,
                                       List<MongoClientSettingsBuilderCustomizer> customizers,
                                       BeanContext beanContext,
                                       BeanDependencyResolver dependencies) {
        GenerationCodecRegistry codecRegistry = new GenerationCodecRegistry(settings.getCodecRegistry());
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers, isConfigured(beanContext))) {
            return DevelopmentMongoClient.over(dependencies.getBean(RetainedSyncMongoClient.class).client(), codecRegistry);
        }
        return DevelopmentMongoClient.owning(MongoClients.create(DevelopmentMongoSettings.ownedSettings(settings, codecRegistry)), codecRegistry);
    }

    /**
     * @param configuration A named configuration
     * @param customizers The settings customizers
     * @param dependencies Resolves the retained client, as a dependency of this one
     * @return The client of the generation, for the factory that declares it
     */
    static DevelopmentMongoClient namedMongoClient(NamedMongoConfiguration configuration,
                                            List<MongoClientSettingsBuilderCustomizer> customizers,
                                            BeanDependencyResolver dependencies) {
        if (DevelopmentMongoSettings.isRetainable(configuration, customizers)) {
            RetainedSyncMongoClient retained = dependencies.getBean(RetainedSyncMongoClient.class, Qualifiers.byName(configuration.getServerName()));
            return DevelopmentMongoClient.over(retained.client(), DevelopmentMongoSettings.codecRegistry(configuration));
        }
        MongoClientSettings settings = configuration.buildSettings();
        GenerationCodecRegistry codecRegistry = new GenerationCodecRegistry(settings.getCodecRegistry());
        return DevelopmentMongoClient.owning(MongoClients.create(DevelopmentMongoSettings.ownedSettings(settings, codecRegistry)), codecRegistry);
    }

    /**
     * Whether the {@link MongoClientSettings} bean is the one {@link DefaultMongoClientSettingsFactory} builds from the
     * configuration, which the retained client copies, rather than one of the application.
     */
    private static boolean isConfigured(BeanContext beanContext) {
        return beanContext.findBeanDefinition(MongoClientSettings.class)
            .flatMap(BeanDefinition::getDeclaringType)
            .filter(DefaultMongoClientSettingsFactory.class::equals)
            .isPresent();
    }
}
