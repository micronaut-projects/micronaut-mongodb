package io.micronaut.mongodb.docs;

import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.uri", value = "mongodb://localhost:27017/app")
@MicronautTest(startApplication = false)
class AutoEncryptionCustomizerTest {

    @Inject
    DefaultMongoConfiguration configuration;

    @Test
    void customizesTheClientSettings() {
        MongoClientSettings settings = configuration.buildSettings();

        assertNotNull(settings.getAutoEncryptionSettings());
        assertEquals("encryption.__keyVault", settings.getAutoEncryptionSettings().getKeyVaultNamespace());
        assertTrue(settings.getAutoEncryptionSettings().getKmsProviders().containsKey("local"));
        assertEquals(UuidRepresentation.STANDARD, settings.getUuidRepresentation());
    }
}
