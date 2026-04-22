package example;

import io.micronaut.configuration.mongo.core.DefaultCodecRegistryBuilder;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.ApplicationConfiguration;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class BsonDiscriminatorTest {
    private static final String TEST_PACKAGE = "example";

    @Test
    void decodesCustomDiscriminatorWithoutPrimingSubtypeCodec() {
        try (ApplicationContext context = ApplicationContext.run()) {
            DefaultMongoConfiguration configuration = new DefaultMongoConfiguration(
                new ApplicationConfiguration(),
                context.getEnvironment()
            );
            configuration.setPackageNames(List.of(TEST_PACKAGE));

            Set<Class<?>> discriminatorEntities = BeanIntrospector.SHARED.findIntrospectedTypes(reference -> {
                    String packageName = reference.getBeanType().getPackageName();
                    return reference.isAnnotationPresent(BsonDiscriminator.class)
                        && (packageName.equals(TEST_PACKAGE) || packageName.startsWith(TEST_PACKAGE + "."));
                }).stream()
                .collect(Collectors.toSet());
            Assertions.assertTrue(discriminatorEntities.contains(Animal.class));
            Assertions.assertTrue(discriminatorEntities.contains(Dog.class));

            CodecRegistry codecRegistry = context.getBean(DefaultCodecRegistryBuilder.class).build(configuration);

            Animal animal = codecRegistry.get(Animal.class).decode(
                new BsonDocumentReader(new BsonDocument()
                    .append("type", new BsonString("Dog"))
                    .append("name", new BsonString("Fido"))
                    .append("favoriteToy", new BsonString("Ball"))),
                DecoderContext.builder().build()
            );

            Assertions.assertInstanceOf(Dog.class, animal);
            Assertions.assertEquals("Fido", animal.getName());
            Assertions.assertEquals("Ball", ((Dog) animal).getFavoriteToy());
        }
    }
}
