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

package io.micronaut.configuration.mongo.reactive.health;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;

import org.bson.Document;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link HealthIndicator} for MongoDB.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = MongoClient.class)
public class MongoHealthIndicator implements HealthIndicator {
    private static final String HEALTH_INDICATOR_NAME = "mongodb";

    private final BeanContext beanContext;
    private final HealthAggregator<?> healthAggregator;
    private final MongoClient[] mongoClients;

    /**
     * @param beanContext beanContext
     * @param healthAggregator healthAggregator
     * @param mongoClients The mongo clients
     */
    public MongoHealthIndicator(BeanContext beanContext, HealthAggregator<?> healthAggregator, MongoClient... mongoClients) {
        this.beanContext = beanContext;
        this.healthAggregator = healthAggregator;
        this.mongoClients = mongoClients;
    }

    @Override
    public Publisher<HealthResult> getResult() {

        List<BeanRegistration<MongoClient>> registrations = getRegisteredConnections();

        Flowable<HealthResult> healthResults = Flowable.fromIterable(registrations)
                .flatMap(this::checkRegisteredMongoClient)
                .onErrorReturn(throwable -> buildStatusDown(throwable, HEALTH_INDICATOR_NAME));

        return this.healthAggregator.aggregate(HEALTH_INDICATOR_NAME, healthResults);
    }

    private List<BeanRegistration<MongoClient>> getRegisteredConnections() {
        return Arrays.stream(mongoClients)
                .map(beanContext::findBeanRegistration)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Publisher<HealthResult> checkRegisteredMongoClient(BeanRegistration<MongoClient> registration) {
        MongoClient mongoClient = registration.getBean();
        String databaseName = "mongodb (" + registration.getIdentifier().getName() + ")";

        Flowable<Map<String, String>> databasePings = Flowable.fromPublisher(pingMongo(mongoClient))
                .map(this::getVersionDetails)
                .timeout(10, SECONDS)
                .retry(3);

        return databasePings.map(detail -> buildStatusUp(databaseName, detail))
                .onErrorReturn(throwable -> buildStatusDown(throwable, databaseName));
    }

    private Publisher<Document> pingMongo(MongoClient mongoClient) {
        return mongoClient.getDatabase("admin").runCommand(new BasicDBObject("buildinfo", "1"));
    }

    private Map<String, String> getVersionDetails(Document document) {
        String version = document.get("version", String.class);
        if (version == null) {
            throw new IllegalStateException("Mongo version not found");
        }
        return Collections.singletonMap("version", version);
    }

    private HealthResult buildStatusUp(String name, Map<String, String> details) {
        HealthResult.Builder builder = HealthResult.builder(name);
        builder.status(HealthStatus.UP);
        builder.details(details);
        return builder.build();
    }

    private HealthResult buildStatusDown(Throwable throwable, String name) {
        HealthResult.Builder builder = HealthResult.builder(name);
        builder.status(HealthStatus.DOWN);
        builder.exception(throwable);
        return builder.build();
    }
}