package example;

import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Introspected
@BsonDiscriminator(key = "type", value = "dog")
public class Dog extends Animal {
    private String favoriteToy;

    public Dog() {
    }

    public String getFavoriteToy() {
        return favoriteToy;
    }

    public void setFavoriteToy(String favoriteToy) {
        this.favoriteToy = favoriteToy;
    }
}
