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
package io.micronaut.mongodb.dev;

import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.MongoClientImpl;
import io.micronaut.configuration.mongo.core.dev.RetainedMongoClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.mongodb.testcontainers.MongoDb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an application that stores documents through the sync and the reactive MongoDB clients through the
 * development runtime, against a MongoDB server, and changes the document class and the repository. A restart keeps
 * the driver clients, which hold the codecs of the driver only, and the next generation encodes and decodes its own
 * classes on them; a change under {@code mongodb} releases them, and nothing of a retired generation stays reachable.
 */
class MongoReloadTest {

    private static final String BOOK = """
        package example;

        public class Book {
            private String title;
            %s

            public Book() {
            }

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }
            %s
        }
        """;

    private static final String BOOK_FIRST = BOOK.formatted("", "");

    private static final String BOOK_SECOND = BOOK.formatted("private String author;", """
            public String getAuthor() {
                return author;
            }

            public void setAuthor(String author) {
                this.author = author;
            }
        """);

    private static final String REPOSITORY = """
        package example;

        import com.mongodb.client.MongoClient;
        import com.mongodb.client.MongoCollection;
        import jakarta.inject.Named;
        import jakarta.inject.Singleton;
        import reactor.core.publisher.Flux;

        import java.util.ArrayList;
        import java.util.List;

        @Singleton
        public class BookRepository {
            private final MongoClient client;
            private final com.mongodb.reactivestreams.client.MongoClient reactiveClient;

            public BookRepository(%s MongoClient client, %s com.mongodb.reactivestreams.client.MongoClient reactiveClient) {
                this.client = client;
                this.reactiveClient = reactiveClient;
            }

            private MongoCollection<Book> books() {
                return client.getDatabase("dev").getCollection("%s", Book.class);
            }

            public void save(String title) {
                Book book = new Book();
                book.setTitle(title);
                %s
                books().insertOne(book);
            }

            public List<String> titles() {
                List<String> titles = new ArrayList<>();
                for (Book book : books().find()) {
                    titles.add(%s);
                }
                return titles;
            }

            public List<String> reactiveTitles() {
                return Flux.from(reactiveClient.getDatabase("dev").getCollection("%s", Book.class).find())
                    .map(book -> %s)
                    .collectList()
                    .block();
            }
        }
        """;

    private static final String LISTENER = """
        package example;

        import com.mongodb.event.CommandListener;
        import com.mongodb.event.CommandStartedEvent;
        import jakarta.inject.Singleton;

        import java.util.concurrent.atomic.AtomicInteger;

        @Singleton
        public class CountingListener implements CommandListener {
            private final AtomicInteger started = new AtomicInteger();

            @Override
            public void commandStarted(CommandStartedEvent event) {
                started.incrementAndGet();
            }

            public int started() {
                return started.get();
            }
        }
        """;

    private static final String NOTE = """
        package example;

        import jakarta.inject.Singleton;

        @Singleton
        public class Note {
            private String text;

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
        """;

    private static final String SETTINGS = """
        package example;

        import com.mongodb.MongoClientSettings;
        import io.micronaut.configuration.mongo.core.DefaultMongoClientSettingsFactory;
        import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
        import io.micronaut.context.annotation.Factory;
        import io.micronaut.context.annotation.Replaces;
        import jakarta.inject.Singleton;

        @Factory
        public class SettingsFactory {
            @Singleton
            @Replaces(bean = MongoClientSettings.class, factory = DefaultMongoClientSettingsFactory.class)
            MongoClientSettings settings(DefaultMongoConfiguration configuration) {
                return MongoClientSettings.builder(configuration.buildSettings()).applicationName("custom").build();
            }
        }
        """;

    private static final String CLIENT_FACTORY = """
        package example;

        import com.mongodb.MongoClientSettings;
        import com.mongodb.client.MongoClient;
        import com.mongodb.client.MongoClients;
        import io.micronaut.configuration.mongo.sync.DefaultMongoClientFactory;
        import io.micronaut.context.annotation.Bean;
        import io.micronaut.context.annotation.Factory;
        import io.micronaut.context.annotation.Replaces;
        import jakarta.inject.Singleton;

        @Factory
        public class ClientFactory {
            @Singleton
            @Bean(preDestroy = "close")
            @Replaces(bean = MongoClient.class, factory = DefaultMongoClientFactory.class)
            MongoClient client(MongoClientSettings settings) {
                return MongoClients.create(settings);
            }
        }
        """;

    private static final String CODEC_REGISTRY_BUILDER = """
        package example;

        import com.mongodb.MongoClientSettings;
        import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
        import io.micronaut.configuration.mongo.core.CodecRegistryBuilder;
        import io.micronaut.configuration.mongo.core.DefaultCodecRegistryBuilder;
        import io.micronaut.context.annotation.Prototype;
        import io.micronaut.context.annotation.Replaces;
        import org.bson.codecs.configuration.CodecRegistries;
        import org.bson.codecs.configuration.CodecRegistry;
        import org.bson.codecs.pojo.PojoCodecProvider;

        @Prototype
        @Replaces(DefaultCodecRegistryBuilder.class)
        public class PojoCodecRegistryBuilder implements CodecRegistryBuilder {
            @Override
            public CodecRegistry build(AbstractMongoConfiguration configuration) {
                return CodecRegistries.fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
                );
            }
        }
        """;

    @TempDir
    Path project;

    @Test
    void aRestartKeepsTheClientsAndTheNextGenerationEncodesItsOwnClasses() throws Exception {
        String collection = "books-restart";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            assertReloaderPresent(harness.context());
            drop(harness.context(), collection);

            invoke(repository(harness.context()), "save", "one");
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
            assertEquals(List.of("one"), invoke(repository(harness.context()), "reactiveTitles"));

            // the sync and the reactive driver clients, which hold none of the application
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(2, retained.size(), "a retained client for the sync and the reactive client");
            Object firstClient = harness.context().getBean(MongoClient.class);

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection, "", ""));
            harness.reload();
            assertEquals(2, harness.generation());
            assertReloaderPresent(harness.context());

            // the second generation runs on the driver clients of the first, with codecs of its own classes
            for (RetainedMongoClient client : retained) {
                ReloadTck.assertRetained(harness, client);
            }
            assertEquals(retained.size(), retained(harness.context()).size());
            assertTrue(retained(harness.context()).containsAll(retained));
            assertNotSame(firstClient, harness.context().getBean(MongoClient.class), "the client bean of a generation is its own");
            firstClient = null;

            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "reactiveTitles"));
            ReloadTck.assertFollowsReload(harness, MongoReloadTest::repository);

            // neither the retained clients nor the codecs of the first generation keep it reachable
            retained = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aChangeUnderTheMongodbPrefixReleasesTheRetainedClients() throws Exception {
        String collection = "books-configuration";
        Map<String, String> properties = MongoDb.getProperties();
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties.forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> first = retained(harness.context());
            assertEquals(2, first.size());

            // the pool configuration changes, together with a class, so the application restarts
            StringBuilder changed = new StringBuilder();
            properties.forEach((key, value) -> changed.append(key).append('=').append(value).append('\n'));
            changed.append("mongodb.connection-pool.max-size=7\n");
            harness.resource("application.properties", changed.toString());
            harness.source("example.BookRepository", secondRepository(collection, "", ""));
            harness.source("example.Book", BOOK_SECOND);
            harness.reload();
            assertEquals(2, harness.generation());
            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));

            // the next generation created its own clients from the changed configuration
            List<RetainedMongoClient> second = retained(harness.context());
            assertEquals(2, second.size());
            for (RetainedMongoClient client : first) {
                assertTrue(second.stream().noneMatch(c -> c == client), "the released client is not adopted");
                assertTrue(isClosed(driverClient(client)), "the released client is closed");
            }
            first = null;
            assertEquals(7, second.stream()
                .map(MongoReloadTest::driverClient)
                .filter(MongoClientImpl.class::isInstance)
                .map(client -> ((MongoClientImpl) client).getSettings().getConnectionPoolSettings().getMaxSize())
                .findFirst()
                .orElseThrow());
            second = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aClassChangedInPlaceRecreatesTheClientBeansOnTheRetainedClients() throws Exception {
        String collection = "books-in-place";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.Note", NOTE);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            Object client = harness.context().getBean(MongoClient.class);
            harness.context().getBean(MongoClient.class).getCodecRegistry().get(type(harness.context(), "example.Note"));

            // a bean changed in place: the context recreates it, the clients stay
            changedInPlace(harness, "example.BookRepository");
            assertSame(client, harness.context().getBean(MongoClient.class));

            // a bean class a codec was built for, changed in place: the client beans build their codecs again
            changedInPlace(harness, "example.Note");
            assertNotSame(client, harness.context().getBean(MongoClient.class), "the codec of a bean class is cached by the client");
            assertTrue(retained(harness.context()).containsAll(retained));
            client = harness.context().getBean(MongoClient.class);
            Object repository = repository(harness.context());

            // a document class changed in place: the client beans build their codecs again, on the same driver clients
            changedInPlace(harness, "example.Book");
            assertNotSame(client, harness.context().getBean(MongoClient.class));
            assertNotSame(repository, repository(harness.context()), "the beans that received a client are recreated");
            assertTrue(retained(harness.context()).containsAll(retained));
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
            assertEquals(List.of("one"), invoke(repository(harness.context()), "reactiveTitles"));
        }
    }

    @Test
    void theClientsOfNamedServersAreRetained() throws Exception {
        String collection = "books-named";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.property("mongodb.servers.alpha.uri", MongoDb.getProperties().get("mongodb.uri"));
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection, "@Named(\"alpha\")", "@Named(\"alpha\")"));
            harness.start();
            drop(harness.context(), collection, "alpha");
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(2, retained.size());

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection, "@Named(\"alpha\")", "@Named(\"alpha\")"));
            harness.reload();
            assertEquals(2, harness.generation());
            for (RetainedMongoClient client : retained) {
                ReloadTck.assertRetained(harness, client);
            }
            retained = null;
            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "reactiveTitles"));
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aSyncClientCreatedWithSettingsOfTheApplicationIsCreatedByEachGeneration() throws Exception {
        String collection = "books-settings";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.SettingsFactory", SETTINGS);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(1, retained.size(), "the reactive client only, which the settings bean does not apply to");
            assertTrue(retained.get(0).getClass().getName().contains("Reactive"));

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection, "", ""));
            harness.reload();
            assertEquals(2, harness.generation());
            ReloadTck.assertRetained(harness, retained.get(0));
            retained = null;
            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aReplacementOfTheClientOfTheFactoryReplacesTheDevelopmentClient() throws Exception {
        String collection = "books-replaced";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.ClientFactory", CLIENT_FACTORY);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            assertEquals(1, harness.context().getBeansOfType(MongoClient.class).size());
            assertTrue(harness.context().getBean(MongoClient.class) instanceof MongoClientImpl, "the client of the application");
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
        }
    }

    @Test
    void aClientACommandListenerAppliesToIsCreatedByEachGeneration() throws Exception {
        String collection = "books-listener";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.CountingListener", LISTENER);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            assertTrue(retained(harness.context()).isEmpty(), "a listener of the application is not kept by a retained client");
            assertTrue((Integer) invoke(harness.context().getBean(type(harness.context(), "example.CountingListener")), "started") > 0);

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection, "", ""));
            harness.reload();
            assertEquals(2, harness.generation());
            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));
            assertTrue((Integer) invoke(harness.context().getBean(type(harness.context(), "example.CountingListener")), "started") > 0,
                "the listener of the second generation is called");
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aRetiredLoaderRecreatesTheConfigurationsWhoseDefaultBuilderCachesDiscriminators() throws Exception {
        String collection = "books-retired-default";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.property("mongodb.package-names", "example");
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(2, retained.size());

            // the default builder may cache discriminator classes of the retired loader: the configuration is
            // recreated, and the retained clients built from it
            retiredLoader(harness);
            for (RetainedMongoClient client : retained) {
                assertTrue(retained(harness.context()).stream().noneMatch(c -> c == client), "the client of the recreated configuration is recreated");
            }
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
            assertEquals(2, retained(harness.context()).size());
        }
    }

    @Test
    void aRetiredLoaderKeepsTheRetainedClientsWhenABuilderWithoutDiscriminatorCacheReplacesTheDefault() throws Exception {
        String collection = "books-retired-replaced";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.property("mongodb.package-names", "example");
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.PojoCodecRegistryBuilder", CODEC_REGISTRY_BUILDER);
            harness.source("example.BookRepository", firstRepository(collection, "", ""));
            harness.start();
            drop(harness.context(), collection);
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(2, retained.size());
            Object client = harness.context().getBean(MongoClient.class);

            // no discriminator cache to drop: the configurations and the retained clients stay, the client beans of
            // the generation build their codecs again
            retiredLoader(harness);
            assertEquals(retained.size(), retained(harness.context()).size());
            assertTrue(retained(harness.context()).containsAll(retained), "the retained clients are kept");
            assertNotSame(client, harness.context().getBean(MongoClient.class));
            for (RetainedMongoClient retainedClient : retained) {
                Object driverClient = driverClient(retainedClient);
                if (driverClient instanceof MongoClientImpl) {
                    assertFalse(isClosed(driverClient), "the retained client is open");
                }
            }
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
            assertEquals(List.of("one"), invoke(repository(harness.context()), "reactiveTitles"));
        }
    }

    private static String firstRepository(String collection, String syncQualifier, String reactiveQualifier) {
        return REPOSITORY.formatted(syncQualifier, reactiveQualifier, collection, "", "book.getTitle()", collection, "book.getTitle()");
    }

    private static String secondRepository(String collection, String syncQualifier, String reactiveQualifier) {
        return REPOSITORY.formatted(syncQualifier, reactiveQualifier, collection, "book.setAuthor(\"second\");",
            "book.getTitle() + \" by \" + book.getAuthor()", collection, "book.getTitle() + \" by \" + book.getAuthor()");
    }

    private static void drop(ApplicationContext context, String collection) {
        context.getBean(MongoClient.class).getDatabase("dev").getCollection(collection).drop();
    }

    private static void drop(ApplicationContext context, String collection, String server) {
        context.getBean(MongoClient.class, Qualifiers.byName(server)).getDatabase("dev").getCollection(collection).drop();
    }

    private static List<RetainedMongoClient> retained(ApplicationContext context) {
        // the active ones only: a lookup by type would create those not created
        List<RetainedMongoClient> beans = new ArrayList<>();
        for (BeanRegistration<RetainedMongoClient> registration : context.getActiveBeanRegistrations(RetainedMongoClient.class)) {
            beans.add(registration.bean());
        }
        return beans;
    }

    private static Object driverClient(RetainedMongoClient retained) {
        try {
            Method client = retained.getClass().getDeclaredMethod("client");
            client.setAccessible(true);
            return client.invoke(retained);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read the driver client of " + retained, e);
        }
    }

    private static boolean isClosed(Object driverClient) {
        try {
            Field closed = driverClient.getClass().getDeclaredField("closed");
            closed.setAccessible(true);
            return ((AtomicBoolean) closed.get(driverClient)).get();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot tell whether " + driverClient + " is closed", e);
        }
    }

    /**
     * Tells the running generation that a class was redefined in place, as the development runtime does after it
     * redefined the class. The context is not kept: a reference to it would keep the generation reachable.
     */
    private static void changedInPlace(ReloadHarness harness, String className) {
        ApplicationContext context = harness.context();
        context.publishEvent(new ClassChangeEvent(MongoReloadTest.class, Set.of(), context.getClassLoader(),
            List.of(new ClassChange(className, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
    }

    /**
     * Tells the running generation that an in-place reload retired a classloader, as the development runtime does
     * when it replaces the loader of the changed classes.
     */
    private static void retiredLoader(ReloadHarness harness) throws Exception {
        ApplicationContext context = harness.context();
        try (URLClassLoader retired = new URLClassLoader(new URL[0], context.getClassLoader())) {
            context.publishEvent(new ClassChangeEvent(MongoReloadTest.class, Set.of(retired), context.getClassLoader(),
                List.of(new ClassChange("example.Book", ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
        }
    }

    private static void assertReloaderPresent(ApplicationContext context) {
        // the bean that follows changes in place exists in development mode only
        assertTrue(context.containsBean(type(context, "io.micronaut.configuration.mongo.core.dev.DevelopmentMongoReloader")));
    }

    private static Object repository(ApplicationContext context) {
        return context.getBean(type(context, "example.BookRepository"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object bean, String method, Object... arguments) {
        try {
            for (Method candidate : bean.getClass().getMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length) {
                    return (T) candidate.invoke(bean, arguments);
                }
            }
            throw new AssertionError(bean.getClass().getName() + " has no method " + method);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke " + method, e);
        }
    }

    private static Class<?> type(ApplicationContext context, String className) {
        try {
            return Class.forName(className, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not in the application", e);
        }
    }
}
