from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from reactor.core.publisher import Flux

from micronaut.mongodb.docs.NamedClientService import NamedClientService


# The MongoDB container URI of the "another" server is supplied by io.micronaut.mongodb.support.MongoTestPropertySourceLoader
# for the "mongodb" environment
@MicronautTest(startApplication=False, environments=["mongodb"])
class NamedClientServiceTest:
    named_client_service: Annotated[NamedClientService, Inject]

    @Test
    def test_lists_the_databases_of_the_named_client(self) -> None:
        names = Flux.from_(self.named_client_service.database_names()).collectList().block()

        assert "admin" in names, str(names)
