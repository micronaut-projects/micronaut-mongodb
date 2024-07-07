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
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.context.scope.Refreshable;

/**
 * Factory for named {@link MongoClient} instances. Creates the injectable {@link io.micronaut.context.annotation.Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class NamedCoroutineMongoClientFactory {

    /**
     * Factory name to create a client.
     * @param configuration configuration pulled in
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @EachBean(NamedMongoConfiguration.class)
    @Refreshable(MongoSettings.PREFIX)
    public com.mongodb.reactivestreams.client.MongoClient mongoClient(NamedMongoConfiguration configuration) {
        return MongoClient.Factory.create(configuration.buildSettings(), null);
    }
}
