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
package io.micronaut.mongodb.docs

// tag::imports[]
import com.mongodb.AutoEncryptionSettings
import com.mongodb.MongoClientSettings
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import jakarta.inject.Singleton
import org.bson.UuidRepresentation
// end::imports[]

// tag::clazz[]
@Singleton
class AutoEncryptionCustomizer : MongoClientSettingsBuilderCustomizer {

    override fun customize(configuration: AbstractMongoConfiguration, clientSettings: MongoClientSettings.Builder) {
        val keyVaultNamespace = if (configuration is NamedMongoConfiguration) {
            "encryption.${configuration.serverName}.__keyVault"
        } else {
            "encryption.__keyVault"
        }

        clientSettings
            .autoEncryptionSettings(AutoEncryptionSettings.builder()
                .keyVaultNamespace(keyVaultNamespace)
                .kmsProviders(mapOf("local" to mapOf("key" to ByteArray(96))))
                .build())
            .uuidRepresentation(UuidRepresentation.STANDARD)
    }
}
// end::clazz[]
