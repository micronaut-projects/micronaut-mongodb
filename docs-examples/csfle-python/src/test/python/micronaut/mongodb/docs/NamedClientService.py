# tag::imports[]
from typing import Annotated

from com.mongodb.reactivestreams.client import MongoClient
from jakarta.inject import Inject, Named, Singleton
from org.reactivestreams import Publisher
# end::imports[]


# tag::clazz[]
@Singleton
class NamedClientService:
    mongo_client: Annotated[MongoClient, Inject, Named("another")]

    def database_names(self) -> Publisher[str]:
        return self.mongo_client.listDatabaseNames()
# end::clazz[]
