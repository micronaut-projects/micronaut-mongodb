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

import com.mongodb.kotlin.client.coroutine.MongoClient;
import io.micronaut.configuration.mongo.core.dev.RetainedMongoClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.mongodb.testcontainers.MongoDb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an application that stores documents through the Kotlin coroutine MongoDB client through the development
 * runtime, against a MongoDB server, and changes the document class and the repository. A restart keeps the reactive
 * driver client under the coroutine client, which holds the codecs of the driver only, and the next generation
 * encodes and decodes its own classes on it, through a coroutine client of its own.
 */
class MongoCoroutineReloadTest {

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

        import com.mongodb.client.model.DropCollectionOptions;
        import com.mongodb.client.model.InsertOneOptions;
        import com.mongodb.kotlin.client.coroutine.MongoClient;
        import com.mongodb.kotlin.client.coroutine.MongoCollection;
        import io.micronaut.configuration.mongo.coroutine.CoroutineSupport;
        import jakarta.inject.Singleton;
        import kotlin.Unit;
        import org.bson.Document;

        import java.util.ArrayList;
        import java.util.List;

        @Singleton
        public class BookRepository {
            private final MongoClient client;

            public BookRepository(MongoClient client) {
                this.client = client;
            }

            private MongoCollection<Book> books() {
                return client.getDatabase("dev").getCollection("%s", Book.class);
            }

            public void drop() {
                CoroutineSupport.<Unit>await(continuation -> books().drop(new DropCollectionOptions(), continuation));
            }

            public void save(String title) {
                Book book = new Book();
                book.setTitle(title);
                %s
                CoroutineSupport.await(continuation -> books().insertOne(book, new InsertOneOptions(), continuation));
            }

            public List<String> titles() {
                List<String> titles = new ArrayList<>();
                CoroutineSupport.<Unit>await(continuation -> books().find(new Document(), Book.class).collect((book, emitted) -> {
                    titles.add(%s);
                    return Unit.INSTANCE;
                }, continuation));
                return titles;
            }
        }
        """;

    @TempDir
    Path project;

    @Test
    void aRestartKeepsTheDriverClientAndTheNextGenerationEncodesItsOwnClasses() throws Exception {
        String collection = "coroutine-books-restart";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection));
            harness.start();
            invoke(repository(harness.context()), "drop");

            invoke(repository(harness.context()), "save", "one");
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));

            // the reactive driver client under the coroutine client, which holds none of the application
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(1, retained.size(), "a retained client for the coroutine client");
            Object firstClient = harness.context().getBean(MongoClient.class);

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection));
            harness.reload();
            assertEquals(2, harness.generation());

            // the second generation runs on the driver client of the first, with codecs of its own classes
            ReloadTck.assertRetained(harness, retained.get(0));
            assertEquals(retained, retained(harness.context()));
            assertNotSame(firstClient, harness.context().getBean(MongoClient.class), "the client bean of a generation is its own");
            firstClient = null;

            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));
            ReloadTck.assertFollowsReload(harness, MongoCoroutineReloadTest::repository);

            // neither the retained client nor the codecs of the first generation keep it reachable
            retained = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aClassChangedInPlaceRecreatesTheCoroutineClientOnTheRetainedClient() throws Exception {
        String collection = "coroutine-books-in-place";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            MongoDb.getProperties().forEach(harness::property);
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection));
            harness.start();
            invoke(repository(harness.context()), "drop");
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(1, retained.size());
            Object client = harness.context().getBean(MongoClient.class);

            // a bean changed in place: the context recreates it, the client stays
            changedInPlace(harness, "example.BookRepository");
            assertSame(client, harness.context().getBean(MongoClient.class));

            // a document class changed in place: the coroutine client is recreated over a driver client of the
            // generation that builds its codecs again, on the same retained client
            changedInPlace(harness, "example.Book");
            assertNotSame(client, harness.context().getBean(MongoClient.class));
            assertEquals(List.of("one"), invoke(repository(harness.context()), "titles"));
            assertEquals(retained, retained(harness.context()));
        }
    }

    @Test
    void theCoroutineClientOfANamedServerIsRetained() throws Exception {
        String collection = "coroutine-books-named";
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.property("mongodb.servers.alpha.uri", MongoDb.getProperties().get("mongodb.uri"));
            harness.source("example.Book", BOOK_FIRST);
            harness.source("example.BookRepository", firstRepository(collection).replace("BookRepository(MongoClient client)", "BookRepository(@jakarta.inject.Named(\"alpha\") MongoClient client)"));
            harness.start();
            invoke(repository(harness.context()), "drop");
            invoke(repository(harness.context()), "save", "one");
            List<RetainedMongoClient> retained = retained(harness.context());
            assertEquals(1, retained.size());

            harness.source("example.Book", BOOK_SECOND);
            harness.source("example.BookRepository", secondRepository(collection).replace("BookRepository(MongoClient client)", "BookRepository(@jakarta.inject.Named(\"alpha\") MongoClient client)"));
            harness.reload();
            assertEquals(2, harness.generation());
            ReloadTck.assertRetained(harness, retained.get(0));
            retained = null;
            invoke(repository(harness.context()), "save", "two");
            assertEquals(List.of("one by null", "two by second"), invoke(repository(harness.context()), "titles"));
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private static String firstRepository(String collection) {
        return REPOSITORY.formatted(collection, "", "book.getTitle()");
    }

    private static String secondRepository(String collection) {
        return REPOSITORY.formatted(collection, "book.setAuthor(\"second\");", "book.getTitle() + \" by \" + book.getAuthor()");
    }

    private static List<RetainedMongoClient> retained(ApplicationContext context) {
        // the active ones only, of the coroutine client: a lookup by type would create those not created
        List<RetainedMongoClient> beans = new ArrayList<>();
        for (BeanRegistration<RetainedMongoClient> registration : context.getActiveBeanRegistrations(RetainedMongoClient.class)) {
            if (registration.bean().getClass().getSimpleName().contains("Coroutine")) {
                beans.add(registration.bean());
            }
        }
        return beans;
    }

    private static void changedInPlace(ReloadHarness harness, String className) {
        ApplicationContext context = harness.context();
        context.publishEvent(new ClassChangeEvent(MongoCoroutineReloadTest.class, Set.of(), context.getClassLoader(),
            List.of(new ClassChange(className, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
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
