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

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import javax.validation.constraints.NotBlank;
import java.util.*;

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
    private Collection<String> packageNames;
    private boolean automaticClassModels = true;

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
     * Whether to allow automatic class models (defaults to true).
     * @param automaticClassModels True if automatic class models should be allowed
     */
    public void setAutomaticClassModels(boolean automaticClassModels) {
        this.automaticClassModels = automaticClassModels;
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
        clientSettings.applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings.build()));
        clientSettings.applyToSocketSettings(builder -> builder.applySettings(socketSettings.build()));
        clientSettings.applyToSslSettings(builder -> builder.applySettings(sslSettings.build()));

        List<CodecRegistry> codecRegistries = new ArrayList<>();
        addDefaultCodecRegistry(codecRegistries);

        if (this.codecRegistries != null) {
            codecRegistries.addAll(this.codecRegistries);
        }
        if (codecList != null) {
            codecRegistries.add(fromCodecs(codecList));
        }

        final PojoCodecProvider.Builder builder = PojoCodecProvider.builder();

        if (CollectionUtils.isNotEmpty(packageNames)) {
            builder.register(packageNames.toArray(new String[0]));
        }

        codecRegistries.add(
                fromProviders(
                        builder.automatic(automaticClassModels).build()
                )
        );

        clientSettings.codecRegistry(
            fromRegistries(codecRegistries)
        );
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
     * Return the appplication name or a default name.
     * @return applicationName
     */
    protected String getApplicationName() {
        return applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
    }
}
