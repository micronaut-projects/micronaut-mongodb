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
package io.micronaut.configuration.mongo.core;

import com.mongodb.MongoClientSettings;

/**
 * Allows arbitrary customization of the {@link MongoClientSettings.Builder}
 * after Micronaut has applied the configuration-backed settings and before
 * the final {@link MongoClientSettings} is built.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@FunctionalInterface
public interface MongoClientSettingsBuilderCustomizer {

    /**
     * Customize the builder for the given configuration.
     *
     * @param configuration The configuration being built
     * @param clientSettings The settings builder
     */
    void customize(AbstractMongoConfiguration configuration, MongoClientSettings.Builder clientSettings);
}
