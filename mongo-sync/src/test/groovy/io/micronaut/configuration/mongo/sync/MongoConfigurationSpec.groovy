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
package io.micronaut.configuration.mongo.sync

import com.mongodb.MongoClientSettings
import com.mongodb.ReadConcern
import com.mongodb.client.MongoClient
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import com.mongodb.event.ConnectionPoolListener
import io.micronaut.configuration.mongo.LowercaseEnum
import io.micronaut.configuration.mongo.LowercaseEnumCodec
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.configuration.mongo.core.MongoSettings
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import jakarta.inject.Singleton
import org.bson.Document
import org.bson.types.ObjectId
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class MongoConfigurationSpec extends Specification {

    @Shared @AutoCleanup GenericContainer mongo =
            new GenericContainer("mongo:4.0")
                    .withExposedPorts(27017)

    def setupSpec() {
        mongo.start()
    }


    void "test a basic blocking driver connection"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://${mongo.containerIpAddress}:${mongo.getMappedPort(27017)}"
        )
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !mongoClient.listDatabaseNames().isEmpty()

        when:
        def coll=   mongoClient.getDatabase("foo").getCollection("bar")

        coll.insertOne(new Document("foo", "bar"))

        then:
        coll.find().first().get("foo") == "bar"

        cleanup:
        applicationContext?.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/703")
    void 'test id property is set automatically after inserting the document'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://${mongo.containerIpAddress}:${mongo.getMappedPort(27017)}"
        )
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        when:
        User user = new User(name: 'John')

        then:
        user.id == null

        when:
        mongoClient.getDatabase('test').getCollection('user', User).insertOne(user)

        then:
        user.name == 'John'
        user.id != null

        cleanup:
        applicationContext.stop()
    }

    static class User {
        ObjectId id
        String name
    }

    void "test build mongo client options"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
            "mongodb.readConcern":'LOCAL'
        )

        DefaultMongoConfiguration configuration = applicationContext.getBean(DefaultMongoConfiguration)
        MongoClientSettings clientOptions = configuration.buildSettings()


        expect:
        clientOptions.readConcern == ReadConcern.LOCAL

        cleanup:
        applicationContext?.close()

    }

    static class SomeEntity {
        LowercaseEnum enumValue
    }

    void "test custom enum codec is used instead of default mongo enum codec"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://${mongo.containerIpAddress}:${mongo.getMappedPort(27017)}"
        )
        applicationContext.registerSingleton(LowercaseEnumCodec.class, new LowercaseEnumCodec())
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        def typedCollection = mongoClient.getDatabase('test').getCollection('lowercase_enum', SomeEntity.class)
        def documentCollection = mongoClient.getDatabase('test').getCollection('lowercase_enum')

        when:
        typedCollection.insertOne(new SomeEntity(
                enumValue: LowercaseEnum.FOO
        ))

        def entityDocument = documentCollection.find()[0]
        def entity = typedCollection.find()[0]

        then:
        entityDocument.getString('enumValue') == 'foo' // our codec encodes the enum values as lower case, the default MongoDB codec will encode as upper case
        entity.enumValue == LowercaseEnum.FOO
    }

    void "test configure pick up custom command listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                "mongodb.url": "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)

        expect:
        configuration.commandListeners.size() == 1

        cleanup:
        context.stop()

    }

    void "test configure pick up custom connection pool listeners"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                "mongodb.url": "mongodb://localhost"
        )

        DefaultMongoConfiguration configuration = context.getBean(DefaultMongoConfiguration)
        MongoClientSettings settings = context.getBean(MongoClientSettings)

        expect:
        configuration.connectionPoolListeners.size() == 1
        settings.connectionPoolSettings.connectionPoolListeners.size() == 1

        cleanup:
        context.stop()
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
