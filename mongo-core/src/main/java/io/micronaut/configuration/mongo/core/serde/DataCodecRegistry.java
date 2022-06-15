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
package io.micronaut.configuration.mongo.core.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Micronaut Serialization codec registry.
 *
 * @author Denis Stepanov
 * @since 4.4
 */
@Internal
public final class DataCodecRegistry implements CodecRegistry {

    @Nullable
    private final Collection<Class<?>> entities;
    private final SerdeRegistry serdeRegistry;
    private final Map<Class, Codec> codecs = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     *
     * @param entities      The entities
     * @param serdeRegistry The serde registry
     */
    public DataCodecRegistry(@Nullable Collection<Class<?>> entities, SerdeRegistry serdeRegistry) {
        this.entities = entities;
        this.serdeRegistry = serdeRegistry;
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz) {
        throw new CodecConfigurationException("Not supported");
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        Codec codec = codecs.get(clazz);
        if (codec != null) {
            return codec;
        }
        if (clazz.isEnum() || entities != null && !entities.contains(clazz)) {
            return null;
        }
        Optional<BeanIntrospection<T>> introspection = BeanIntrospector.SHARED.findIntrospection(clazz);
        if (introspection.isPresent()) {
            BeanIntrospection<T> beanIntrospection = introspection.get();
            if (beanIntrospection.hasStereotype(Serdeable.Serializable.class)
                || beanIntrospection.hasStereotype(Serdeable.Deserializable.class)) {
                codec = new SerdeCodec<>(serdeRegistry, clazz, registry);
                codecs.put(clazz, codec);
                return codec;
            }
        }
        return null;
    }

}
