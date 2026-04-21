package example;

import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Introspected
@BsonDiscriminator(key = "type")
public class Animal {
    private String name;

    public Animal() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
