package io.micronaut.mongodb.docs

import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.servers.another.uri", value = "mongodb://localhost:27018/app")
@MicronautTest(startApplication = false)
class NamedAutoEncryptionCustomizerTest {

    @Inject
    @field:Named("another")
    lateinit var configuration: NamedMongoConfiguration

    @Test
    fun usesTheServerNameInTheKeyVaultNamespace() {
        val settings = configuration.buildSettings()

        assertNotNull(settings.autoEncryptionSettings)
        assertEquals("encryption.another.__keyVault", settings.autoEncryptionSettings!!.keyVaultNamespace)
    }
}
