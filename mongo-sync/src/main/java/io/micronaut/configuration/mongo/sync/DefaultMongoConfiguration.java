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

package io.micronaut.configuration.mongo.sync;

import com.mongodb.MongoClientSettings;
import com.mongodb.connection.*;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.ApplicationConfiguration;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.List;

/**
 * The default MongoDB configuration class.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(property = MongoSettings.PREFIX)
@Requires(classes = MongoClientSettings.class)
@ConfigurationProperties(MongoSettings.PREFIX)
public class DefaultMongoConfiguration extends AbstractMongoConfiguration {

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "options")
    protected MongoClientSettings.Builder clientOptions = MongoClientSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "cluster")
    protected ClusterSettings.Builder clusterSettings = ClusterSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "server")
    protected ServerSettings.Builder serverSettings = ServerSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "connection-pool")
    protected ConnectionPoolSettings.Builder poolSettings = ConnectionPoolSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "socket")
    protected SocketSettings.Builder socketSettings = SocketSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "ssl")
    protected SslSettings.Builder sslSettings = SslSettings.builder();

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    public DefaultMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    @Override
    public ClusterSettings.Builder getClusterSettings() {
        return clusterSettings;
    }

    @Override
    public MongoClientSettings.Builder getClientSettings() {
        return clientOptions;
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

    /**
     * @return Builds the {@link MongoClientSettings}
     */
    @Override
    public MongoClientSettings buildSettings() {
        clientOptions.applicationName(getApplicationName());
        return clientOptions.build();
    }

    @Override
    protected void addDefaultCodecRegistry(List<CodecRegistry> codecRegistries) {
        codecRegistries.add(MongoClientSettings.getDefaultCodecRegistry());
    }

}
