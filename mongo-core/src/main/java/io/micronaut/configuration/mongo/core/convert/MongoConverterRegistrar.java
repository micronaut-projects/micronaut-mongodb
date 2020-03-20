package io.micronaut.configuration.mongo.core.convert;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;

import javax.inject.Singleton;

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
