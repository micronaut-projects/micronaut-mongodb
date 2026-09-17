package io.micronaut.mongodb.docs

import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.UuidRepresentation
import spock.lang.Specification

@Property(name = "spec.name", value = "AutoEncryptionCustomizerTest")
@Property(name = "mongodb.uri", value = "mongodb://localhost:27017/app")
@MicronautTest(startApplication = false)
class AutoEncryptionCustomizerSpec extends Specification {

    @Inject
    DefaultMongoConfiguration configuration

    void "customizes the client settings"() {
        when:
        def settings = configuration.buildSettings()

        then:
        settings.autoEncryptionSettings
        settings.autoEncryptionSettings.keyVaultNamespace == "encryption.__keyVault"
        settings.autoEncryptionSettings.kmsProviders.containsKey("local")
        settings.uuidRepresentation == UuidRepresentation.STANDARD
    }
}
