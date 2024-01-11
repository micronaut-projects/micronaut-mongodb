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

import com.mongodb.reactivestreams.client.MongoClient
import io.micronaut.configuration.mongo.coroutine.health.MongoHealthIndicator
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.management.health.indicator.HealthResult
import org.testcontainers.containers.GenericContainer
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.time.Duration

import static io.micronaut.configuration.mongo.core.MongoSettings.MONGODB_URI
import static io.micronaut.health.HealthStatus.DOWN
import static io.micronaut.health.HealthStatus.UP
import static java.time.temporal.ChronoUnit.SECONDS

class MongoHealthIndicatorSpec extends Specification {

    void "test mongo health indicator disabled"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                PropertySource.mapOf(
                        MONGODB_URI, "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
                        "endpoints.health.mongodb.enabled", false)
        )
        applicationContext.getBean(MongoHealthIndicator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        applicationContext.close()
    }

    void "test mongo health indicator DOWN"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}"
        )
        try {
            Flux.from(applicationContext.getBean(MongoClient).listDatabaseNames())
                    .timeout(Duration.of(2, SECONDS))
                    .blockFirst()
        } catch (ignored) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        then:
        Flux.from(healthIndicator.result).blockFirst().status == DOWN

        cleanup:
        applicationContext.close()
    }

    void "test mongo health indicator UP"() {
        given:
        GenericContainer mongo = new GenericContainer("mongo:4.0")
                .withExposedPorts(27017)
        mongo.start()

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MONGODB_URI): "mongodb://$mongo.containerIpAddress:${mongo.getMappedPort(27017)}"
        )
        try {
            Flux.from(applicationContext.getBean(MongoClient).listDatabaseNames())
                    .timeout(Duration.of(2, SECONDS))
                    .blockFirst()
        } catch (ignored) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        HealthResult healthResult = Flux.from(healthIndicator.result).blockFirst()

        then:
        healthResult.status == UP
        healthResult.details.containsKey("mongodb (Primary)")

        cleanup:
        mongo.close()
        applicationContext.close()
    }
}
