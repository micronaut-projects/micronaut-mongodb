package io.micronaut.mongodb.docs

import io.micronaut.mongodb.testcontainers.MongoDb
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(startApplication = false)
class NamedClientServiceTest : TestPropertyProvider {

    override fun getProperties(): Map<String, String> =
        mapOf("mongodb.servers.another.uri" to MongoDb.getProperties()["mongodb.uri"]!!)

    @Inject
    lateinit var namedClientService: NamedClientService

    @Test
    fun listsTheDatabasesOfTheNamedClient() {
        val names = Flux.from(namedClientService.databaseNames()).collectList().block()!!

        assertTrue(names.contains("admin"), names.toString())
    }
}
