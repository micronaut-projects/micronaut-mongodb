/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.configuration.mongo.sync;

import com.mongodb.ClientBulkWriteException;
import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoDriverInformation;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCluster;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.bulk.ClientBulkWriteOptions;
import com.mongodb.client.model.bulk.ClientBulkWriteResult;
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel;
import com.mongodb.connection.ClusterDescription;
import io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry;
import io.micronaut.configuration.mongo.core.dev.GenerationMongoClient;
import io.micronaut.core.annotation.Internal;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The {@link MongoClient} bean of a generation in development mode. Over a retained client, it runs the operations on
 * the cluster of that client with the codec registry of the generation, so that the retained client holds no codec
 * of the application, and closing it leaves the retained client open. Over a client of its own, which development
 * mode does not retain, it runs them on that client and closes it.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
final class DevelopmentMongoClient implements MongoClient, GenerationMongoClient {

    private final MongoClient client;
    private final MongoCluster cluster;
    private final boolean owned;
    private final GenerationCodecRegistry codecs;

    private DevelopmentMongoClient(MongoClient client, MongoCluster cluster, boolean owned, GenerationCodecRegistry codecs) {
        this.client = client;
        this.cluster = cluster;
        this.owned = owned;
        this.codecs = codecs;
    }

    /**
     * @param retained The retained client
     * @param codecRegistry The codec registry of the generation
     * @return A client that runs the operations on the retained one with the codecs of the generation
     */
    static DevelopmentMongoClient over(MongoClient retained, GenerationCodecRegistry codecRegistry) {
        return new DevelopmentMongoClient(retained, retained.withCodecRegistry(codecRegistry), false, codecRegistry);
    }

    /**
     * @param client A client of the generation
     * @param codecRegistry The codec registry the client was created with
     * @return A client that runs the operations on it and closes it
     */
    static DevelopmentMongoClient owning(MongoClient client, GenerationCodecRegistry codecRegistry) {
        return new DevelopmentMongoClient(client, client, true, codecRegistry);
    }

    @Override
    public boolean isCodecRequested(String className) {
        return codecs.isRequested(className);
    }

    @Override
    public void close() {
        if (owned) {
            client.close();
        }
    }

    @Override
    public ClusterDescription getClusterDescription() {
        return client.getClusterDescription();
    }

    @Override
    public void appendMetadata(MongoDriverInformation mongoDriverInformation) {
        client.appendMetadata(mongoDriverInformation);
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return cluster.getCodecRegistry();
    }

    @Override
    public ReadPreference getReadPreference() {
        return cluster.getReadPreference();
    }

    @Override
    public WriteConcern getWriteConcern() {
        return cluster.getWriteConcern();
    }

    @Override
    public ReadConcern getReadConcern() {
        return cluster.getReadConcern();
    }

    @Override
    public Long getTimeout(TimeUnit timeUnit) {
        return cluster.getTimeout(timeUnit);
    }

    @Override
    public MongoCluster withCodecRegistry(CodecRegistry codecRegistry) {
        return cluster.withCodecRegistry(codecRegistry);
    }

    @Override
    public MongoCluster withReadPreference(ReadPreference readPreference) {
        return cluster.withReadPreference(readPreference);
    }

    @Override
    public MongoCluster withWriteConcern(WriteConcern writeConcern) {
        return cluster.withWriteConcern(writeConcern);
    }

    @Override
    public MongoCluster withReadConcern(ReadConcern readConcern) {
        return cluster.withReadConcern(readConcern);
    }

    @Override
    public MongoCluster withTimeout(long timeout, TimeUnit timeUnit) {
        return cluster.withTimeout(timeout, timeUnit);
    }

    @Override
    public MongoDatabase getDatabase(String databaseName) {
        return cluster.getDatabase(databaseName);
    }

    @Override
    public ClientSession startSession() {
        return cluster.startSession();
    }

    @Override
    public ClientSession startSession(ClientSessionOptions options) {
        return cluster.startSession(options);
    }

    @Override
    public MongoIterable<String> listDatabaseNames() {
        return cluster.listDatabaseNames();
    }

    @Override
    public MongoIterable<String> listDatabaseNames(ClientSession clientSession) {
        return cluster.listDatabaseNames(clientSession);
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases() {
        return cluster.listDatabases();
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases(ClientSession clientSession) {
        return cluster.listDatabases(clientSession);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(Class<TResult> resultClass) {
        return cluster.listDatabases(resultClass);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(ClientSession clientSession, Class<TResult> resultClass) {
        return cluster.listDatabases(clientSession, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch() {
        return cluster.watch();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> resultClass) {
        return cluster.watch(resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(List<? extends Bson> pipeline) {
        return cluster.watch(pipeline);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return cluster.watch(pipeline, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession) {
        return cluster.watch(clientSession);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, Class<TResult> resultClass) {
        return cluster.watch(clientSession, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession, List<? extends Bson> pipeline) {
        return cluster.watch(clientSession, pipeline);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return cluster.watch(clientSession, pipeline, resultClass);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models) throws ClientBulkWriteException {
        return cluster.bulkWrite(models);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models, ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return cluster.bulkWrite(models, options);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models) throws ClientBulkWriteException {
        return cluster.bulkWrite(clientSession, models);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models, ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return cluster.bulkWrite(clientSession, models, options);
    }
}
