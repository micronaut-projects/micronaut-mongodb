package io.micronaut.mongodb.docs

import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.UuidRepresentation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.uri", value = "mongodb://localhost:27017/app")
@MicronautTest(startApplication = false)
class AutoEncryptionCustomizerTest {

    @Inject
    lateinit var configuration: DefaultMongoConfiguration

    @Test
    fun customizesTheClientSettings() {
        val settings = configuration.buildSettings()

        assertNotNull(settings.autoEncryptionSettings)
        assertEquals("encryption.__keyVault", settings.autoEncryptionSettings!!.keyVaultNamespace)
        assertTrue(settings.autoEncryptionSettings!!.kmsProviders.containsKey("local"))
        assertEquals(UuidRepresentation.STANDARD, settings.uuidRepresentation)
    }
}
