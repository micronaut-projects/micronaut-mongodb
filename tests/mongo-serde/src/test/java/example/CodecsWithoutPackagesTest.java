package example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.mongodb.testcontainers.MongoDb;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class CodecsWithoutPackagesTest {

    @Test
    void readsAndWritesSerdeableTypesWithoutPackages() {
        Map<String, Object> properties = new HashMap<>(MongoDb.getProperties());
        try (ApplicationContext context = builder().properties(properties).start()) {
            Assertions.assertTrue(context.getEnvironment().getPackages().isEmpty());
            MongoCollection<Book> books = context.getBean(MongoClient.class)
                .getDatabase("without-packages")
                .getCollection("books", Book.class);
            books.insertOne(new Book("The Stand", 1000));

            Book found = books.find().first();
            Assertions.assertNotNull(found);
            Assertions.assertNotNull(found.getId());
            Assertions.assertEquals("The Stand", found.getTitle());
        }
    }

    @Test
    void hasCodecsOnlyInThePackagesThereAre() {
        Map<String, Object> properties = Map.of("mongodb.uri", "mongodb://localhost:1");
        try (ApplicationContext context = builder().properties(properties).packages("other").start()) {
            MongoClient client = context.getBean(MongoClient.class);
            Assertions.assertThrows(
                CodecConfigurationException.class,
                () -> client.getDatabase("x").getCodecRegistry().get(Book.class)
            );
        }
        try (ApplicationContext context = builder().properties(properties).packages("example").start()) {
            MongoClient client = context.getBean(MongoClient.class);
            Assertions.assertNotNull(client.getDatabase("x").getCodecRegistry().get(Book.class));
        }
    }

    private static ApplicationContextBuilder builder() {
        return ApplicationContext.builder().deducePackage(false);
    }
}
