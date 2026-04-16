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
package io.micronaut.configuration.mongo.reactive

import com.mongodb.AutoEncryptionSettings
import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer
import io.micronaut.configuration.mongo.core.MongoSettings
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import org.bson.UuidRepresentation
import spock.lang.Specification

class MongoClientSettingsBuilderCustomizerSpec extends Specification {
    private static final String SPEC_NAME = 'mongo-client-settings-builder-customizer-reactive'

    void "test configure picks up custom MongoClientSettings builder customizers for default reactive client"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost",
                'spec.name': SPEC_NAME
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings settings = configuration.buildSettings()
        MongoClient mongoClient = context.getBean(MongoClient)

        expect:
        mongoClient != null
        settings.autoEncryptionSettings.keyVaultNamespace == "encryption.__keyVault"
        settings.uuidRepresentation == UuidRepresentation.STANDARD

        cleanup:
        context.stop()
    }

    void "test configure picks up custom MongoClientSettings builder customizers for named reactive client"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                'mongodb.servers.myServer.uri': "mongodb://localhost:27017",
                'spec.name': SPEC_NAME
        )

        NamedMongoConfiguration configuration = context.getBean(NamedMongoConfiguration, Qualifiers.byName('my-server'))
        MongoClientSettings settings = configuration.buildSettings()
        MongoClient mongoClient = context.getBean(MongoClient, Qualifiers.byName('my-server'))

        expect:
        mongoClient != null
        settings.autoEncryptionSettings.keyVaultNamespace == "encryption.my-server.__keyVault"
        settings.uuidRepresentation == UuidRepresentation.STANDARD

        cleanup:
        context.stop()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class AutoEncryptionCustomizer implements MongoClientSettingsBuilderCustomizer {

        @Override
        void customize(AbstractMongoConfiguration configuration, MongoClientSettings.Builder clientSettings) {
            String keyVaultNamespace = configuration instanceof NamedMongoConfiguration
                    ? "encryption.${configuration.serverName}.__keyVault"
                    : "encryption.__keyVault"
            clientSettings
                    .autoEncryptionSettings(AutoEncryptionSettings.builder()
                            .keyVaultNamespace(keyVaultNamespace)
                            .kmsProviders([local: [key: new byte[96]]] as Map<String, Map<String, Object>>)
                            .build())
                    .uuidRepresentation(UuidRepresentation.STANDARD)
        }
    }
}
