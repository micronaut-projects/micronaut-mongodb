/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.mongo.reactive

import com.mongodb.MongoClientSettings
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.client.result.InsertOneResult
import com.mongodb.reactivestreams.client.MongoClient
import groovy.transform.NotYetImplemented
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.configuration.mongo.core.MongoSettings
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.reactivex.Single
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Singleton

class MongoReactiveConfigurationSpec extends Specification {

    @Shared @AutoCleanup GenericContainer mongo =
            new GenericContainer("mongo:4.0")
                    .withExposedPorts(27017)

    def setupSpec() {
        mongo.start()
    }

    void "test connection with connection string"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://${mongo.containerIpAddress}:${mongo.getMappedPort(27017)}"
        )
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList().isEmpty()

        when: "A POJO is saved"
        InsertOneResult success = Single.fromPublisher(mongoClient.getDatabase("test").getCollection("test", Book).insertOne(new Book(
                title: "The Stand"
        ))).blockingGet()

        then:
        success != null
        success.wasAcknowledged()

        cleanup:
        applicationContext.stop()
    }

    @Unroll
    void "test configure #property client setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ("${MongoSettings.PREFIX}.${property}".toString()): value
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()


        expect:
        clientSettings."$property" == expected

        and:
        context.stop()

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
                "mongodb.url": "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)

        expect:
        configuration.codecs.size() == 1

        cleanup:
        context.stop()

    }

    @Unroll
    void "test configure #property pool setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${mongo.exposedPorts[0]}",
                ("${MongoSettings.PREFIX}.connectionPool.${property}".toString()): value
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()


        expect:
        clientSettings.connectionPoolSettings."$property" == value

        and:
        context.stop()

        where:
        property  | value
        "maxSize" | 10
        "minSize" | 5


    }

    @Unroll
    @NotYetImplemented
    // FIXME: specifying URI overrides cluster settings
    void "test configure #property cluster setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,
                (MongoSettings.MONGODB_URI): "mongodb://localhost:27017"

        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings.clusterSettings."$property" == value

        and:
        context.stop()

        where:
        property           | value
        "maxWaitQueueSize" | 5
    }

    @Unroll
    void "test configure #property cluster setting for hosts"() {

        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,

        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings.clusterSettings."$readProperty" == expected


        and:
        context.stop()

        where:
        property                 | value | readProperty               | expected
        "serverSelectionTimeout" | "60s" | 'serverSelectionTimeoutMS' | 60000
    }

    @Unroll
    void "test configure #property pool setting for named server"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                'mongodb.servers.myServer.uri': "mongodb://localhost:27017",
                ("mongodb.servers.myServer.connectionPool.${property}".toString()): value
        )

        NamedMongoConfiguration configuration = context.getBean(NamedMongoConfiguration, Qualifiers.byName('my-server'))
        MongoClientSettings clientSettings = configuration.buildSettings()
        MongoClient mongoClient = context.getBean(MongoClient, Qualifiers.byName('my-server'))

        expect:
        mongoClient != null
        clientSettings.connectionPoolSettings."$property" == value

        where:
        property  | value
        "maxSize" | 10
    }

    static class Book {
        String title
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
}
