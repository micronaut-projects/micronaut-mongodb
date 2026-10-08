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
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The codec registry of a generation in development mode. It delegates to the registry the configuration builds, and
 * records the names of the classes a codec was asked for, so that the reloader recreates the client beans when one of
 * those classes changes in place, a bean class included, since the driver caches a codec by class. It keeps names
 * only, never a class. A lookup made on this registry itself goes through a registry of the driver that resolves the
 * nested codecs through this one, so that the classes of the properties are recorded too.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
public final class GenerationCodecRegistry implements CodecRegistry {

    private final CodecRegistry delegate;
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final CodecRegistry lookup;

    /**
     * @param delegate The registry built by the configuration
     */
    public GenerationCodecRegistry(CodecRegistry delegate) {
        this.delegate = delegate;
        this.lookup = CodecRegistries.fromProviders(new Recorder());
    }

    /**
     * @param className The name of a class
     * @return Whether a codec was asked for the class
     */
    public boolean isRequested(String className) {
        return requested.contains(className);
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz) {
        return lookup.get(clazz);
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, List<Type> typeArguments) {
        return lookup.get(clazz, typeArguments);
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        requested.add(clazz.getName());
        return delegate.get(clazz, registry);
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, List<Type> typeArguments, CodecRegistry registry) {
        requested.add(clazz.getName());
        return delegate.get(clazz, typeArguments, registry);
    }

    /**
     * Provides the codecs of this registry to the lookup registry, which passes itself for the nested codecs.
     */
    private final class Recorder implements CodecProvider {
        @Override
        public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
            return GenerationCodecRegistry.this.get(clazz, registry);
        }

        @Override
        public <T> Codec<T> get(Class<T> clazz, List<Type> typeArguments, CodecRegistry registry) {
            return GenerationCodecRegistry.this.get(clazz, typeArguments, registry);
        }
    }
}
