package example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
class BookTest extends AbstractMongoSpec {

    @Inject
    MongoClient mongoClient;

    @Test
    void testCrud() {
        MongoDatabase defaultDatabase = mongoClient.getDatabase("default");
        MongoCollection<Book> books = defaultDatabase.getCollection("books", Book.class);
        Book book = new Book("The Stand", 1000);
        books.insertOne(book);

        Book foundBook = books.find().iterator().next();
        Assertions.assertNotNull(foundBook.getId());
        Assertions.assertEquals("The Stand", foundBook.getTitle());
    }

}
