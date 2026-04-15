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

import com.mongodb.client.MongoClient;
import io.micronaut.configuration.mongo.core.MongoClientCloser;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Cleans up tracked blocking Mongo clients when beans are destroyed.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Internal
@Requires(classes = MongoClient.class)
@Singleton
final class MongoClientShutdownListener implements BeanDestroyedEventListener<MongoClient> {

    private final MongoClientCloser mongoClientCloser;

    /**
     * @param mongoClientCloser Tracks configured shutdown delays
     */
    MongoClientShutdownListener(MongoClientCloser mongoClientCloser) {
        this.mongoClientCloser = mongoClientCloser;
    }

    @Override
    public void onDestroyed(BeanDestroyedEvent<MongoClient> event) {
        mongoClientCloser.untrack(event.getBean());
    }
}
