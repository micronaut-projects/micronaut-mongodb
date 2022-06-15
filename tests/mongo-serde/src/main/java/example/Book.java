
package example;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

@Serdeable
public class Book {
    @BsonId
    private ObjectId id;
    private String title;
    private int pages;
    public Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }
}
