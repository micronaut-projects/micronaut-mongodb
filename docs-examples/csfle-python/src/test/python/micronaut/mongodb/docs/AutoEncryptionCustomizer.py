# tag::imports[]
from com.mongodb import AutoEncryptionSettings, MongoClientSettings
from jakarta.inject import Singleton
from micronaut.configuration.mongo.core import (
    AbstractMongoConfiguration,
    MongoClientSettingsBuilderCustomizer,
    NamedMongoConfiguration,
)
from org.bson import UuidRepresentation
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="AutoEncryptionCustomizerTest")
# tag::clazz[]
@Singleton
class AutoEncryptionCustomizer(MongoClientSettingsBuilderCustomizer):

    def customize(self, configuration: AbstractMongoConfiguration, client_settings: MongoClientSettings.Builder) -> None:
        key_vault_namespace = "encryption.__keyVault"
        if isinstance(configuration, NamedMongoConfiguration):
            key_vault_namespace = f"encryption.{configuration.getServerName()}.__keyVault"

        kms_providers = {"local": {"key": bytes(96)}}

        (client_settings
            .autoEncryptionSettings(AutoEncryptionSettings.builder()
                .keyVaultNamespace(key_vault_namespace)
                .kmsProviders(kms_providers)
                .build())
            .uuidRepresentation(UuidRepresentation.STANDARD))
# end::clazz[]
