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
package io.micronaut.configuration.mongo.reactive;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoDriverInformation;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.bulk.ClientBulkWriteOptions;
import com.mongodb.client.model.bulk.ClientBulkWriteResult;
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.ListDatabasesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCluster;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry;
import io.micronaut.configuration.mongo.core.dev.GenerationMongoClient;
import io.micronaut.core.annotation.Internal;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The reactive {@link MongoClient} bean of a generation in development mode. Over a retained client, it runs the operations on
 * the cluster of that client with the codec registry of the generation, so that the retained client holds no codec
 * of the application, and closing it leaves the retained client open. Over a client of its own, which development
 * mode does not retain, it runs them on that client and closes it.
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
final class DevelopmentReactiveMongoClient implements MongoClient, GenerationMongoClient {

    private final MongoClient client;
    private final MongoCluster cluster;
    private final boolean owned;
    private final GenerationCodecRegistry codecs;

    private DevelopmentReactiveMongoClient(MongoClient client, MongoCluster cluster, boolean owned, GenerationCodecRegistry codecs) {
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
    static DevelopmentReactiveMongoClient over(MongoClient retained, GenerationCodecRegistry codecRegistry) {
        return new DevelopmentReactiveMongoClient(retained, retained.withCodecRegistry(codecRegistry), false, codecRegistry);
    }

    /**
     * @param client A client of the generation
     * @param codecRegistry The codec registry the client was created with
     * @return A client that runs the operations on it and closes it
     */
    static DevelopmentReactiveMongoClient owning(MongoClient client, GenerationCodecRegistry codecRegistry) {
        return new DevelopmentReactiveMongoClient(client, client, true, codecRegistry);
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
    public Publisher<ClientSession> startSession() {
        return cluster.startSession();
    }

    @Override
    public Publisher<ClientSession> startSession(ClientSessionOptions options) {
        return cluster.startSession(options);
    }

    @Override
    public Publisher<String> listDatabaseNames() {
        return cluster.listDatabaseNames();
    }

    @Override
    public Publisher<String> listDatabaseNames(ClientSession clientSession) {
        return cluster.listDatabaseNames(clientSession);
    }

    @Override
    public ListDatabasesPublisher<Document> listDatabases() {
        return cluster.listDatabases();
    }

    @Override
    public ListDatabasesPublisher<Document> listDatabases(ClientSession clientSession) {
        return cluster.listDatabases(clientSession);
    }

    @Override
    public <TResult> ListDatabasesPublisher<TResult> listDatabases(Class<TResult> resultClass) {
        return cluster.listDatabases(resultClass);
    }

    @Override
    public <TResult> ListDatabasesPublisher<TResult> listDatabases(ClientSession clientSession, Class<TResult> resultClass) {
        return cluster.listDatabases(clientSession, resultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch() {
        return cluster.watch();
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(Class<TResult> resultClass) {
        return cluster.watch(resultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(List<? extends Bson> pipeline) {
        return cluster.watch(pipeline);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return cluster.watch(pipeline, resultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(ClientSession clientSession) {
        return cluster.watch(clientSession);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(ClientSession clientSession, Class<TResult> resultClass) {
        return cluster.watch(clientSession, resultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(ClientSession clientSession, List<? extends Bson> pipeline) {
        return cluster.watch(clientSession, pipeline);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(ClientSession clientSession, List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return cluster.watch(clientSession, pipeline, resultClass);
    }

    @Override
    public Publisher<ClientBulkWriteResult> bulkWrite(List<? extends ClientNamespacedWriteModel> models) {
        return cluster.bulkWrite(models);
    }

    @Override
    public Publisher<ClientBulkWriteResult> bulkWrite(List<? extends ClientNamespacedWriteModel> models, ClientBulkWriteOptions options) {
        return cluster.bulkWrite(models, options);
    }

    @Override
    public Publisher<ClientBulkWriteResult> bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models) {
        return cluster.bulkWrite(clientSession, models);
    }

    @Override
    public Publisher<ClientBulkWriteResult> bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models, ClientBulkWriteOptions options) {
        return cluster.bulkWrite(clientSession, models, options);
    }
}
