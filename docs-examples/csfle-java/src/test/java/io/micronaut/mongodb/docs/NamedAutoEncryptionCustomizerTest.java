package io.micronaut.mongodb.docs;

import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.servers.another.uri", value = "mongodb://localhost:27018/app")
@MicronautTest(startApplication = false)
class NamedAutoEncryptionCustomizerTest {

    @Inject
    @Named("another")
    NamedMongoConfiguration configuration;

    @Test
    void usesTheServerNameInTheKeyVaultNamespace() {
        MongoClientSettings settings = configuration.buildSettings();

        assertNotNull(settings.getAutoEncryptionSettings());
        assertEquals("encryption.another.__keyVault", settings.getAutoEncryptionSettings().getKeyVaultNamespace());
    }
}
