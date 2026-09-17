package io.micronaut.mongodb.docs

// tag::imports[]
import com.mongodb.reactivestreams.client.MongoClient
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
// end::imports[]

// tag::clazz[]
@Singleton
class NamedClientService {

    @Inject
    @field:Named("another")
    lateinit var mongoClient: MongoClient

    fun databaseNames(): Publisher<String> = mongoClient.listDatabaseNames()
}
// end::clazz[]
