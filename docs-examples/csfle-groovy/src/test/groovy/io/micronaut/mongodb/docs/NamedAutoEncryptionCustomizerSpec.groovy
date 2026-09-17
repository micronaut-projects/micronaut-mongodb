package io.micronaut.mongodb.docs

import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.servers.another.uri", value = "mongodb://localhost:27018/app")
@MicronautTest(startApplication = false)
class NamedAutoEncryptionCustomizerSpec extends Specification {

    @Inject
    @Named("another")
    NamedMongoConfiguration configuration

    void "uses the server name in the key vault namespace"() {
        when:
        def settings = configuration.buildSettings()

        then:
        settings.autoEncryptionSettings
        settings.autoEncryptionSettings.keyVaultNamespace == "encryption.another.__keyVault"
    }
}
