package io.micronaut.configuration.mongo.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.context.scope.Refreshable;

/**
 * Factory for named {@link MongoClient} instances. Creates the injectable {@link io.micronaut.context.annotation.Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class NamedMongoClientFactory {

    /**
     * Factory name to create a client.
     * @param configuration configuration pulled in
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @EachBean(NamedMongoConfiguration.class)
    @Refreshable(MongoSettings.PREFIX)
    MongoClient mongoClient(NamedMongoConfiguration configuration) {
        return MongoClients.create(configuration.buildSettings());
    }
}
