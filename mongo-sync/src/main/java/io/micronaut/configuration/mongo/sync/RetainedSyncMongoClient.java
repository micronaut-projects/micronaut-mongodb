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

import com.mongodb.client.MongoClient;
import io.micronaut.configuration.mongo.core.dev.RetainedMongoClient;
import io.micronaut.core.annotation.Internal;

/**
 * A driver client development mode retains across a restart. It is built from the configuration values with the
 * default codec registry of the driver, and is used through a {@link DevelopmentMongoClient} of each generation.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
final class RetainedSyncMongoClient implements RetainedMongoClient {

    private final MongoClient client;

    /**
     * @param client The driver client
     */
    RetainedSyncMongoClient(MongoClient client) {
        this.client = client;
    }

    /**
     * @return The driver client
     */
    MongoClient client() {
        return client;
    }

    @Override
    public void close() {
        client.close();
    }
}
