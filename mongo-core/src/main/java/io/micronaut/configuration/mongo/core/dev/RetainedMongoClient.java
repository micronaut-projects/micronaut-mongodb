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

import io.micronaut.core.annotation.Internal;

/**
 * A MongoDB driver client that development mode retains across a restart of the application: it is built from the
 * configuration values only, with the codecs of the MongoDB driver and none of the application, so that the next
 * generation adopts it and runs its operations on it with codecs of its own, through a {@link GenerationMongoClient}.
 * A change under {@code mongodb} releases it.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
public interface RetainedMongoClient extends AutoCloseable {

    /**
     * Closes the driver client.
     */
    @Override
    void close();
}
