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
package io.micronaut.configuration.mongo.core.convert;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;

import jakarta.inject.Singleton;

/**
 * A converter registrar for Mongo converters.
 */
@Singleton
@Internal
class MongoConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                ReadConcern.class,
                new StringToReadConcernConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                ReadPreference.class,
                new StringToReadPreferenceConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                ServerAddress.class,
                new StringToServerAddressConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                WriteConcern.class,
                new StringToWriteConcernConverter()
        );
    }
}
