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
package io.micronaut.configuration.mongo.coroutine

import com.mongodb.MongoClientSettings
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import com.mongodb.event.ConnectionPoolListener
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.configuration.mongo.core.MongoSettings
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import spock.lang.Specification
import spock.lang.Unroll

class MongoCoroutineConfigurationSpec extends Specification {

    void "test default coroutine mongo client is injectable"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:27017"
        )

        expect:
        context.getBean(MongoClient) != null

        cleanup:
        context.close()
    }

    void "test named coroutine mongo client is injectable"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ("${MongoSettings.PREFIX}.servers.analytics.uri".toString()): "mongodb://localhost:27018"
        )

        expect:
        context.getBean(MongoClient, Qualifiers.byName("analytics")) != null

        cleanup:
        context.close()
    }

    void "test default coroutine mongo client remains primary when named clients exist"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:27017",
                ("${MongoSettings.PREFIX}.servers.analytics.uri".toString()): "mongodb://localhost:27018"
        )

        when:
        MongoClient defaultClient = context.getBean(MongoClient)
        MongoClient analyticsClient = context.getBean(MongoClient, Qualifiers.byName("analytics"))

        then:
        defaultClient != null
        analyticsClient != null
        defaultClient.is(context.getBean(MongoClient))

        cleanup:
        context.close()
    }

    @Unroll
    void "test configure #property client setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ("${MongoSettings.PREFIX}.${property}".toString()): value,
                (MongoSettings.MONGODB_URI): "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings."$property" == expected

        cleanup:
        context.close()

        where:
        property         | value          | expected
        "readConcern"    | "LINEARIZABLE" | ReadConcern.LINEARIZABLE
        "writeConcern"   | "W1"           | WriteConcern.W1
        "readPreference" | "SECONDARY"    | ReadPreference.secondary()
    }

    void "test configure pick up custom codecs"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)

        expect:
        configuration.codecs.size() == 1

        cleanup:
        context.close()
    }

    void "test configure pick up custom command listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)

        expect:
        configuration.commandListeners.size() == 1

        cleanup:
        context.close()
    }

    void "test configure pick up custom connection pool listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings settings = context.getBean(MongoClientSettings)

        expect:
        configuration.connectionPoolListeners.size() == 1
        settings.connectionPoolSettings.connectionPoolListeners.size() == 1

        cleanup:
        context.close()
    }

    void "test configure pool setting for named server"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                'mongodb.servers.myServer.uri': "mongodb://localhost:27017",
                'mongodb.servers.myServer.connectionPool.maxSize': 10
        )

        NamedMongoConfiguration configuration = context.getBean(NamedMongoConfiguration, Qualifiers.byName('my-server'))
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        context.getBean(MongoClient, Qualifiers.byName('my-server')) != null
        clientSettings.connectionPoolSettings.maxSize == 10

        cleanup:
        context.close()
    }

    static class Fluff {
    }

    @Singleton
    static class FluffCodec implements Codec<Fluff> {

        @Override
        Fluff decode(BsonReader reader, DecoderContext decoderContext) {
            reader.readString()
            return new Fluff()
        }

        @Override
        void encode(BsonWriter writer, Fluff value, EncoderContext encoderContext) {
            writer.writeString("fluff")
        }

        @Override
        Class<Fluff> getEncoderClass() {
            Fluff
        }
    }

    @Singleton
    static class FluffCommandListener implements CommandListener {

        @Override
        void commandStarted(CommandStartedEvent event) {
        }

        @Override
        void commandSucceeded(CommandSucceededEvent event) {
        }

        @Override
        void commandFailed(CommandFailedEvent event) {
        }
    }

    @Singleton
    static class FluffConnectionPoolListener implements ConnectionPoolListener {
    }
}
