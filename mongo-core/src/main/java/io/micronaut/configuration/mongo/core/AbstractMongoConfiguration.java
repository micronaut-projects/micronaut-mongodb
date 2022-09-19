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

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

import javax.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract Mongo configuration type.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractMongoConfiguration {

    private String uri;

    private final ApplicationConfiguration applicationConfiguration;
    private List<Codec<?>> codecList = Collections.emptyList();
    private List<CodecRegistry> codecRegistries = Collections.emptyList();
    private List<CommandListener> commandListeners = Collections.emptyList();
    private List<ConnectionPoolListener> connectionPoolListeners = Collections.emptyList();
    private Collection<String> packageNames;
    private boolean automaticClassModels = true;
    private CodecRegistryBuilder codecRegistryBuilder;
    private boolean useSerde;

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    protected AbstractMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * Additional codecs to register.
     *
     * @param codecList The list of codecs
     */
    public void codecs(List<Codec<?>> codecList) {
        if (codecList != null) {
            this.codecList = codecList;
        }
    }

    /**
     * Additional codecs to register.
     *
     * @param codecRegistries The list of codecs
     */
    public void codecRegistries(List<CodecRegistry> codecRegistries) {
        if (codecRegistries != null) {
            this.codecRegistries = codecRegistries;
        }
    }

    /**
     * Additional command listeners to register.
     *
     * @param commandListeners The list of command listeners
     */
    public void commandListeners(List<CommandListener> commandListeners) {
        if (commandListeners != null) {
            this.commandListeners = commandListeners;
        }
    }

    /**
     * Additional command listeners to register.
     *
     * @param connectionPoolListeners The list of command listeners
     */
    public void connectionPoolListeners(List<ConnectionPoolListener> connectionPoolListeners) {
        if (connectionPoolListeners != null) {
            this.connectionPoolListeners = connectionPoolListeners;
        }
    }

    /**
     * Additional codecs to register.
     *
     * @param packageNames The package names
     */
    public void packages(Collection<String> packageNames) {
        if (packageNames != null) {
            this.packageNames = packageNames;
        }
    }

    /**
     * The configured codecs.
     * @return The codecs
     */
    public List<Codec<?>> getCodecs() {
        return codecList;
    }

    /**
     * The configured codec registries.
     * @return The registries
     */
    public List<CodecRegistry> getCodecRegistries() {
        return codecRegistries;
    }

    /**
     * The configured command listeners.
     * @return The command listeners
     */
    public List<CommandListener> getCommandListeners() {
        return commandListeners;
    }

    /**
     * The configured connection pool listeners.
     * @return The connection pool listeners
     */
    public List<ConnectionPoolListener> getConnectionPoolListeners() {
        return connectionPoolListeners;
    }

    /**
     * @return The MongoDB URI
     */
    @NotBlank
    public String getUri() {
        return uri;
    }

    /**
     * Sets the MongoDB URI.
     *
     * @param uri The MongoDB URI
     */
    public void setUri(String uri) {
        this.uri = uri;
        Optional<ConnectionString> connectionString = getConnectionString();
        if (connectionString.isPresent()) {
            ConnectionString cs = connectionString.get();
            getClientSettings().applyConnectionString(cs);
            getServerSettings().applyConnectionString(cs);
            getClusterSettings().applyConnectionString(cs);
            getPoolSettings().applyConnectionString(cs);
            getSslSettings().applyConnectionString(cs);
            getSocketSettings().applyConnectionString(cs);
        }
    }

    /**
     * The package names to allow for POJOs.
     *
     * @param packageNames The package names
     */
    public void setPackageNames(Collection<String> packageNames) {
        this.packageNames = packageNames;
    }

    /**
     * @return The package names to allow for POJOs.
     */
    public Collection<String> getPackageNames() {
        return packageNames;
    }

    /**
     * Whether to allow automatic class models (defaults to true).
     * @param automaticClassModels True if automatic class models should be allowed
     */
    public void setAutomaticClassModels(boolean automaticClassModels) {
        this.automaticClassModels = automaticClassModels;
    }

    /**
     * @return Whether to allow automatic class models (defaults to true).
     */
    public boolean isAutomaticClassModels() {
        return automaticClassModels;
    }

    /**
     * @return The MongoDB {@link ConnectionString}
     */
    public Optional<ConnectionString> getConnectionString() {
        if (StringUtils.isNotEmpty(uri)) {
            return Optional.of(new ConnectionString(uri));
        }
        return Optional.empty();
    }

    /**
     * @return The {@link ClusterSettings#builder()}
     */
    public abstract ClusterSettings.Builder getClusterSettings();

    /**
     * @return The {@link MongoClientSettings#builder()}
     */
    public abstract MongoClientSettings.Builder getClientSettings();

    /**
     * @return The {@link ServerSettings#builder()}
     */
    public abstract ServerSettings.Builder getServerSettings();

    /**
     * @return The {@link ConnectionPoolSettings#builder()}
     */
    public abstract ConnectionPoolSettings.Builder getPoolSettings();

    /**
     * @return The {@link SocketSettings#builder()}
     */
    public abstract SocketSettings.Builder getSocketSettings();

    /**
     * @return The {@link SslSettings#builder()}
     */
    public abstract SslSettings.Builder getSslSettings();

    /**
     * @return Builds the {@link MongoClientSettings}
     */
    public MongoClientSettings buildSettings() {
        ClusterSettings.Builder clusterSettings = getClusterSettings();
        SslSettings.Builder sslSettings = getSslSettings();
        ConnectionPoolSettings.Builder poolSettings = getPoolSettings();
        SocketSettings.Builder socketSettings = getSocketSettings();
        ServerSettings.Builder serverSettings = getServerSettings();

        MongoClientSettings.Builder clientSettings = getClientSettings();
        clientSettings.applicationName(getApplicationName());
        clientSettings.applyToClusterSettings(builder -> builder.applySettings(clusterSettings.build()));
        clientSettings.applyToServerSettings(builder -> builder.applySettings(serverSettings.build()));
        clientSettings.applyToConnectionPoolSettings(builder -> {
            builder.applySettings(poolSettings.build());
            connectionPoolListeners.forEach(builder::addConnectionPoolListener);
        });
        clientSettings.applyToSocketSettings(builder -> builder.applySettings(socketSettings.build()));
        clientSettings.applyToSslSettings(builder -> builder.applySettings(sslSettings.build()));
        clientSettings.codecRegistry(codecRegistryBuilder.build(this));
        clientSettings.commandListenerList(commandListeners);
        return clientSettings.build();
    }

    /**
     * Adds the default codec registry.
     * @param codecRegistries The codec registries
     */
    protected void addDefaultCodecRegistry(List<CodecRegistry> codecRegistries) {
        codecRegistries.add(MongoClientSettings.getDefaultCodecRegistry());
    }

    /**
     * @param codecRegistryBuilder The builder
     */
    public void setCodecRegistryBuilder(CodecRegistryBuilder codecRegistryBuilder) {
        this.codecRegistryBuilder = codecRegistryBuilder;
    }

    /**
     * Return the appplication name or a default name.
     * @return applicationName
     */
    protected String getApplicationName() {
        return applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
    }

    /**
     * @return useSerde
     */
    public boolean isUseSerde() {
        return useSerde;
    }

    /**
     * Activates Micronaut Serialization instead of MongoDB POJO.
     *
     * @param useSerde true if to activate
     */
    public void setUseSerde(boolean useSerde) {
        this.useSerde = useSerde;
    }
}
