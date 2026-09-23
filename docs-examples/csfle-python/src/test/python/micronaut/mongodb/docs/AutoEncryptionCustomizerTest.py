from typing import Annotated

from jakarta.inject import Inject
from micronaut.configuration.mongo.core import DefaultMongoConfiguration
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.bson import UuidRepresentation
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="AutoEncryptionCustomizerTest")
@Property(name="mongodb.uri", value="mongodb://localhost:27017/app")
@MicronautTest(startApplication=False)
class AutoEncryptionCustomizerTest:
    configuration: Annotated[DefaultMongoConfiguration, Inject]

    @Test
    def test_customizes_the_client_settings(self) -> None:
        settings = self.configuration.buildSettings()

        assert settings.getAutoEncryptionSettings() is not None
        assert settings.getAutoEncryptionSettings().getKeyVaultNamespace() == "encryption.__keyVault"
        assert settings.getAutoEncryptionSettings().getKmsProviders().containsKey("local")
        assert settings.getUuidRepresentation() == UuidRepresentation.STANDARD
