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

import com.mongodb.ReadPreference
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient
import com.mongodb.reactivestreams.client.MongoDatabase as ReactiveMongoDatabase
import io.micronaut.configuration.mongo.coroutine.health.CoroutineMongoHealthIndicator
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.HealthResult
import org.bson.Document
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import reactor.core.publisher.Flux
import spock.lang.IgnoreIf
import spock.lang.Specification
import java.util.Optional

import static io.micronaut.configuration.mongo.core.MongoSettings.MONGODB_URI
import static io.micronaut.health.HealthStatus.DOWN
import static io.micronaut.health.HealthStatus.UP

class CoroutineMongoHealthIndicatorSpec extends Specification {

    void "test mongo health indicator uses buildInfo command"() {
        given:
        ReactiveMongoClient reactiveMongoClient = Mock()
        ReactiveMongoDatabase reactiveMongoDatabase = Mock()
        MongoClient mongoClient = new MongoClient(reactiveMongoClient)
        BeanRegistration<MongoClient> registration = Mock() {
            getBean() >> mongoClient
            getIdentifier() >> BeanIdentifier.of("Primary")
            getBeanDefinition() >> Stub(BeanDefinition)
        }
        BeanContext beanContext = Stub() {
            findBeanRegistration(_ as MongoClient) >> Optional.of(registration)
        }
        HealthAggregator<?> healthAggregator = Stub() {
            aggregate("mongodb-coroutine", _) >> { String name, org.reactivestreams.Publisher<HealthResult> results -> results }
        }
        CoroutineMongoHealthIndicator healthIndicator = new CoroutineMongoHealthIndicator(beanContext, healthAggregator, mongoClient)

        when:
        HealthResult healthResult = Flux.from(healthIndicator.result).blockFirst()

        then:
        1 * reactiveMongoClient.getDatabase("admin") >> reactiveMongoDatabase
        1 * reactiveMongoDatabase.getReadPreference() >> ReadPreference.primary()
        1 * reactiveMongoDatabase.runCommand({
            it.containsKey("buildInfo") && !it.containsKey("buildinfo")
        }, ReadPreference.primary(), Document) >> Flux.just(new Document("version", "1.2.3"))
        healthResult.status == UP
        healthResult.details.version == "1.2.3"
    }

    void "test mongo health indicator disabled"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                PropertySource.of([MONGODB_URI: "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}", "endpoints.health.mongodb-coroutine.enabled": false])
        )
        applicationContext.getBean(CoroutineMongoHealthIndicator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        applicationContext.close()
    }

    void "test mongo health indicator DOWN"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
                'mongodb.cluster.serverSelectionTimeout': '1s'
        )
        try {
            CoroutineSupport.await { continuation ->
                MongoClient mongoClient = applicationContext.getBean(MongoClient)
                def database = mongoClient.getDatabase("admin")
                database.runCommandDocument(new Document("buildInfo", 1), database.getReadPreference(), continuation)
            }
        } catch (ignored) {
        }
        CoroutineMongoHealthIndicator healthIndicator = applicationContext.getBean(CoroutineMongoHealthIndicator)

        then:
        Flux.from(healthIndicator.result).blockFirst().status == DOWN

        cleanup:
        applicationContext.close()
    }

    @IgnoreIf({ !DockerClientFactory.instance().isDockerAvailable() })
    void "test mongo health indicator UP"() {
        given:
        GenericContainer mongo = new GenericContainer("mongo:5.0")
                .withExposedPorts(27017)
        mongo.start()

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MONGODB_URI): "mongodb://${mongo.host}:${mongo.getMappedPort(27017)}"
        )
        try {
            CoroutineSupport.await { continuation ->
                MongoClient mongoClient = applicationContext.getBean(MongoClient)
                def database = mongoClient.getDatabase("admin")
                database.runCommandDocument(new Document("buildInfo", 1), database.getReadPreference(), continuation)
            }
        } catch (ignored) {
        }
        CoroutineMongoHealthIndicator healthIndicator = applicationContext.getBean(CoroutineMongoHealthIndicator)
        HealthResult healthResult = Flux.from(healthIndicator.result).blockFirst()

        then:
        healthResult.status == UP
        healthResult.details.containsKey("mongodb (Primary)")

        cleanup:
        mongo.close()
        applicationContext.close()
    }
}
