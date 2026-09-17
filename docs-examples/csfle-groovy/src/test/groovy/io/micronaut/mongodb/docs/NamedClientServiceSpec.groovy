package io.micronaut.mongodb.docs

import io.micronaut.mongodb.testcontainers.MongoDb
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification

@MicronautTest(startApplication = false)
class NamedClientServiceSpec extends Specification implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ["mongodb.servers.another.uri": MongoDb.getProperties().get("mongodb.uri")]
    }

    @Inject
    NamedClientService namedClientService

    void "lists the databases of the named client"() {
        when:
        List<String> names = Flux.from(namedClientService.databaseNames()).collectList().block()

        then:
        names.contains("admin")
    }
}
