package io.micronaut.configuration.mongo;

import jakarta.inject.Singleton;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

@Singleton
public class LowercaseEnumCodec implements Codec<LowercaseEnum> {
    @Override
    public LowercaseEnum decode(BsonReader reader, DecoderContext decoderContext) {
        String rawEnum = reader.readString();
        return LowercaseEnum.valueOf(rawEnum.toUpperCase());
    }

    @Override
    public void encode(BsonWriter writer, LowercaseEnum value, EncoderContext encoderContext) {
        writer.writeString(value.toString().toLowerCase());
    }

    @Override
    public Class<LowercaseEnum> getEncoderClass() {
        return LowercaseEnum.class;
    }
}
