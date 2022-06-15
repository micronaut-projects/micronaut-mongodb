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
package io.micronaut.configuration.mongo.core;

import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.serde.DataCodecRegistry;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Default builder.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Prototype
@Requires(missingBeans = CodecRegistryBuilder.class)
@Internal
public final class DefaultCodecRegistryBuilder implements CodecRegistryBuilder {

    private final Environment environment;
    private final BeanProvider<SerdeRegistry> serdeRegistry;

    public DefaultCodecRegistryBuilder(Environment environment, BeanProvider<SerdeRegistry> serdeRegistry) {
        this.environment = environment;
        this.serdeRegistry = serdeRegistry;
    }

    @Override
    public CodecRegistry build(AbstractMongoConfiguration configuration) {
        List<CodecRegistry> codecRegistries = new ArrayList<>();

        List<CodecRegistry> configuredCodecRegistries = configuration.getCodecRegistries();
        if (configuredCodecRegistries != null) {
            codecRegistries.addAll(configuredCodecRegistries);
        }
        List<Codec<?>> codecList = configuration.getCodecs();
        if (codecList != null) {
            codecRegistries.add(fromCodecs(codecList));
        }

        codecRegistries.add(MongoClientSettings.getDefaultCodecRegistry());

        Collection<String> packageNames = configuration.getPackageNames();
        if (configuration.isUseSerde()) {
            Collection<Class<?>> entities = Stream.concat(
                environment.scan(Serdeable.Serializable.class, packageNames.toArray(new String[0])),
                environment.scan(Serdeable.Deserializable.class, packageNames.toArray(new String[0]))
            ).collect(Collectors.toSet());
            codecRegistries.add(new DataCodecRegistry(entities, serdeRegistry.find(null)
                .orElseThrow(() -> new IllegalStateException("SerdeRegistry is not configured! Make sure you have added Micronaut Serialization BSON dependency."))));
        } else {
            final PojoCodecProvider.Builder builder = PojoCodecProvider.builder();
            if (CollectionUtils.isNotEmpty(packageNames)) {
                builder.register(packageNames.toArray(new String[0]));
            }
            codecRegistries.add(
                fromProviders(
                    builder.automatic(configuration.isAutomaticClassModels()).build()
                )
            );
        }
        return fromRegistries(codecRegistries);
    }
}
