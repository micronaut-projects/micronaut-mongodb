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
package io.micronaut.configuration.mongo.core.dev;

import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.core.annotation.Internal;
import org.bson.codecs.configuration.CodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decides whether development mode retains a MongoDB client, and builds the settings of a retained one.
 *
 * <p>A retained client outlives the generation that created it, so it must hold nothing of it. Its codec registry is
 * the default one of the driver: the codecs a generation builds from the application, a POJO or a Micronaut
 * Serialization codec of an application class, the codecs and registries the application declares as beans, are
 * applied by the generation to the operations it runs, with {@code MongoCluster#withCodecRegistry}. The command and
 * connection pool listeners and the settings customizers are beans the driver keeps and calls on every operation, and
 * a customizer can put anything in the settings: a client they apply to is not retained, but created by each
 * generation, as outside development mode.</p>
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
public final class DevelopmentMongoSettings {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentMongoSettings.class);

    private DevelopmentMongoSettings() {
    }

    /**
     * Whether a client of the configuration can be retained across a restart: no command listener, connection pool
     * listener or settings customizer applies to it.
     *
     * @param configuration The configuration
     * @param customizers The settings customizers, which the configuration received too
     * @return Whether the client can be retained
     */
    public static boolean isRetainable(AbstractMongoConfiguration configuration, List<MongoClientSettingsBuilderCustomizer> customizers) {
        if (configuration.getCommandListeners().isEmpty()
            && configuration.getConnectionPoolListeners().isEmpty()
            && customizers.isEmpty()) {
            return true;
        }
        LOG.info("The MongoDB client [{}] is created again on each development restart, rather than retained: command listeners, connection pool listeners or settings customizers apply to it, which the driver would keep",
            configuration instanceof NamedMongoConfiguration named ? named.getServerName() : "default");
        return false;
    }

    /**
     * The settings of a retained client: those of the configuration, with the default codec registry of the driver.
     *
     * @param configuration The configuration
     * @return The settings, which hold nothing of the generation
     */
    public static MongoClientSettings retainedSettings(AbstractMongoConfiguration configuration) {
        return MongoClientSettings.builder(configuration.buildSettings())
            .codecRegistry(MongoClientSettings.getDefaultCodecRegistry())
            .build();
    }

    /**
     * The codec registry of the generation for a configuration.
     *
     * @param configuration The configuration
     * @return The registry, built anew
     */
    public static CodecRegistry codecRegistry(AbstractMongoConfiguration configuration) {
        return configuration.buildSettings().getCodecRegistry();
    }
}
