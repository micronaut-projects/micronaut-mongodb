from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.configuration.mongo.core import NamedMongoConfiguration
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="AutoEncryptionCustomizerTest")
@Property(name="mongodb.servers.another.uri", value="mongodb://localhost:27018/app")
@MicronautTest(startApplication=False)
class NamedAutoEncryptionCustomizerTest:
    configuration: Annotated[NamedMongoConfiguration, Inject, Named("another")]

    @Test
    def test_uses_the_server_name_in_the_key_vault_namespace(self) -> None:
        settings = self.configuration.buildSettings()

        assert settings.getAutoEncryptionSettings() is not None
        assert settings.getAutoEncryptionSettings().getKeyVaultNamespace() == "encryption.another.__keyVault"
