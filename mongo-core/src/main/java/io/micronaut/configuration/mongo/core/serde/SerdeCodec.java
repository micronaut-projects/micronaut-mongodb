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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;

import java.io.IOException;

/**
 * Serde implementation of {@link Codec}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 4.4
 */
class SerdeCodec<T> implements Codec<T> {

    protected final SerdeRegistry dataSerdeRegistry;
    protected final Class<T> type;
    protected final Argument<T> argument;
    protected final Serializer<? super T> serializer;
    protected final Deserializer<? extends T> deserializer;
    protected final CodecRegistry codecRegistry;
    private final Deserializer.DecoderContext decoderContext;
    private final Serializer.EncoderContext encoderContext;

    /**
     * Default constructor.
     *
     * @param dataSerdeRegistry The data serde registry
     * @param type              The type
     * @param codecRegistry     The codec registry
     */
    SerdeCodec(SerdeRegistry dataSerdeRegistry, Class<T> type, CodecRegistry codecRegistry) {
        this.dataSerdeRegistry = dataSerdeRegistry;
        this.type = type;
        this.argument = Argument.of(type);
        this.codecRegistry = codecRegistry;
        this.decoderContext = dataSerdeRegistry.newDecoderContext(type);
        this.encoderContext = dataSerdeRegistry.newEncoderContext(type);
        try {
            this.serializer = dataSerdeRegistry.findSerializer(argument).createSpecific(encoderContext, argument);
            this.deserializer = dataSerdeRegistry.findDeserializer(argument).createSpecific(decoderContext, argument);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find serialize/deserializer for type: " + type + ". " + e.getMessage(), e);
        }
    }

    @Override
    public T decode(BsonReader reader, DecoderContext decoderContext) {
        try {
            return deserializer.deserialize(new BsonReaderDecoder(reader, LimitingStream.DEFAULT_LIMITS), this.decoderContext, argument);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot deserialize: " + type, e);
        }
    }

    @Override
    public void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
        try {
            serializer.serialize(new BsonWriterEncoder(writer, LimitingStream.DEFAULT_LIMITS), this.encoderContext, argument, value);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize: " + value, e);
        }
    }

    @Override
    public Class<T> getEncoderClass() {
        return type;
    }
}
