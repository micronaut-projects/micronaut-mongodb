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
package io.micronaut.configuration.mongo.core.dev;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * The values under {@code mongodb} that a default driver client retained across a development restart is built from.
 *
 * <p>The client is built from this configuration rather than from the one of the generation, which receives the
 * environment, the codecs, the listeners and the settings customizers: development mode refuses to retain a bean whose
 * configuration holds what is bound to the context. This configuration receives none of them, and builds settings
 * with the default codec registry of the driver. It is only exposed under its own type, so that nothing that asks for
 * a {@link AbstractMongoConfiguration} sees it.</p>
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
@DevelopmentActive
@Requires(property = MongoSettings.PREFIX)
@Requires(missingProperty = MongoSettings.MONGODB_SERVERS)
@ConfigurationProperties(MongoSettings.PREFIX)
@Bean(typed = RetainedDefaultMongoConfiguration.class)
public final class RetainedDefaultMongoConfiguration extends AbstractMongoConfiguration {

    @ConfigurationBuilder(prefixes = "")
    MongoClientSettings.Builder clientSettings = MongoClientSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "cluster")
    ClusterSettings.Builder clusterSettings = ClusterSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "server")
    ServerSettings.Builder serverSettings = ServerSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "connection-pool")
    ConnectionPoolSettings.Builder poolSettings = ConnectionPoolSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "socket")
    SocketSettings.Builder socketSettings = SocketSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "ssl")
    SslSettings.Builder sslSettings = SslSettings.builder();

    /**
     * @param applicationConfiguration The application configuration, which names the client
     */
    public RetainedDefaultMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        setCodecRegistryBuilder(configuration -> MongoClientSettings.getDefaultCodecRegistry());
    }

    /**
     * Sets the server MongoDB server address.
     *
     * @param serverAddress The server address
     */
    public void setHost(ServerAddress serverAddress) {
        getClusterSettings().hosts(Collections.singletonList(serverAddress));
    }

    /**
     * Sets the server MongoDB server addresses.
     *
     * @param serverAddresses The server addresses
     */
    public void setHosts(List<ServerAddress> serverAddresses) {
        if (serverAddresses != null) {
            getClusterSettings().hosts(serverAddresses);
        }
    }

    @Override
    public ClusterSettings.Builder getClusterSettings() {
        return clusterSettings;
    }

    @Override
    public MongoClientSettings.Builder getClientSettings() {
        return clientSettings;
    }

    @Override
    public ServerSettings.Builder getServerSettings() {
        return serverSettings;
    }

    @Override
    public ConnectionPoolSettings.Builder getPoolSettings() {
        return poolSettings;
    }

    @Override
    public SocketSettings.Builder getSocketSettings() {
        return socketSettings;
    }

    @Override
    public SslSettings.Builder getSslSettings() {
        return sslSettings;
    }
}
