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
package io.micronaut.configuration.mongo.reactive


import io.micronaut.configuration.mongo.core.MongoSettings
import io.micronaut.configuration.mongo.reactive.health.MongoHealthIndicator
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.health.HealthStatus
import io.reactivex.Flowable
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class MongoHealthIndicatorSpec extends Specification {

    void "test mongo health indicator DOWN"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}"
        )
        try {
            Flowable.fromPublisher(applicationContext.getBean(com.mongodb.reactivestreams.client.MongoClient).listDatabaseNames()).timeout(2, TimeUnit.SECONDS).blockingFirst()
        } catch (e) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        then:
        Flowable.fromPublisher(healthIndicator.result).blockingFirst().status == HealthStatus.DOWN

        cleanup:
        applicationContext.close()
    }

    void "test mongo health indicator UP"() {
        given:
        GenericContainer mongo =
                new GenericContainer("mongo:4.0")
        mongo.start()

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://${mongo.containerIpAddress}:${mongo.getMappedPort(27017)}"
        )
        try {
            Flowable.fromPublisher(applicationContext.getBean(com.mongodb.reactivestreams.client.MongoClient).listDatabaseNames()).timeout(2, TimeUnit.SECONDS).blockingFirst()
        } catch (e) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        def healthResult = Flowable.fromPublisher(healthIndicator.result).blockingFirst()
        then:
        healthResult.status == HealthStatus.UP
        healthResult.details.containsKey("mongodb (Primary)")

        cleanup:
        mongo.close()
        applicationContext.close()
    }
}
