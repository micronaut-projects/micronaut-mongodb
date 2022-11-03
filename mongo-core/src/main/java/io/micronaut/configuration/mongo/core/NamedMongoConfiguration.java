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
package io.micronaut.configuration.mongo.core;

import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.event.CommandListener;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.List;

/**
 * Creates a named configuration for each entry under {@link MongoSettings#MONGODB_SERVERS}.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(value = MongoSettings.MONGODB_SERVERS, primary = "default")
public class NamedMongoConfiguration extends AbstractMongoConfiguration {

    @ConfigurationBuilder(prefixes = "")
    protected MongoClientSettings.Builder clientSettings = MongoClientSettings.builder();

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

    private final String serverName;

    /**
     * Constructor.
     * @param serverName serverName from properties
     * @param applicationConfiguration applicationConfiguration
     */
    public NamedMongoConfiguration(@Parameter String serverName, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.serverName = serverName;
    }


    /**
     * Constructor.
     * @param serverName serverName from properties
     * @param applicationConfiguration applicationConfiguration
     * @param environment The environment
     */
    @Inject public NamedMongoConfiguration(@Parameter String serverName, ApplicationConfiguration applicationConfiguration, Environment environment) {
        super(applicationConfiguration);
        this.serverName = serverName;
        if (environment != null) {
            setPackageNames(environment.getPackages());
        }
    }

    @Override
    @Inject
    public void codecs(List<Codec<?>> codecList) {
        super.codecs(codecList);
    }

    @Override
    @Inject
    public void codecRegistries(List<CodecRegistry> codecRegistries) {
        super.codecRegistries(codecRegistries);
    }

    @Override
    @Inject
    public void setCodecRegistryBuilder(CodecRegistryBuilder codecRegistryBuilder) {
        super.setCodecRegistryBuilder(codecRegistryBuilder);
    }

    @Override
    @Inject
    public void commandListeners(List<CommandListener> commandListeners) {
        super.commandListeners(commandListeners);
    }

    /**
     * @return The name of the server
     */
    public String getServerName() {
        return serverName;
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

    @Override
    protected String getApplicationName() {
        String applicationName = super.getApplicationName();
        return applicationName + "-" + serverName;
    }
}
