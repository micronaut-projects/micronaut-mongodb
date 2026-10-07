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
 * The MongoDB client bean of a generation in development mode. It runs the operations on a
 * {@link RetainedMongoClient} with the codec registry of the generation, built from the codecs, the entities and the
 * serialization of the application as they are, or, when the client cannot be retained, on a driver client of its own.
 * Recreated when a class its codecs may be built from changes, which leaves the retained client as it is.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
public interface GenerationMongoClient {
}
