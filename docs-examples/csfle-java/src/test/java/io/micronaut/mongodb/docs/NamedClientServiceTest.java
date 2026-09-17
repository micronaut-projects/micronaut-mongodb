package io.micronaut.mongodb.docs;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.mongodb.testcontainers.MongoDb;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(startApplication = false)
class NamedClientServiceTest implements TestPropertyProvider {

    @Override
    public @NonNull Map<String, String> getProperties() {
        return Map.of("mongodb.servers.another.uri", MongoDb.getProperties().get("mongodb.uri"));
    }

    @Inject
    NamedClientService namedClientService;

    @Test
    void listsTheDatabasesOfTheNamedClient() {
        List<String> names = Flux.from(namedClientService.databaseNames()).collectList().block();

        assertTrue(names.contains("admin"), names.toString());
    }
}
