/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.mongo.core.dev

import com.mongodb.MongoClientSettings
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A retained client must talk to the server, and report the name, that the client of a generation does.
 */
class RetainedMongoConfigurationSpec extends Specification {

    private static final String URI = 'mongodb://uri.test:27111'

    @Unroll
    void "the retained default configuration connects where the normal one does for #properties"() {
        given:
        ApplicationContext context = run(properties)

        when:
        MongoClientSettings normal = context.getBean(DefaultMongoConfiguration).buildSettings()
        MongoClientSettings retained = DevelopmentMongoSettings.retainedSettings(context.getBean(RetainedDefaultMongoConfiguration))

        then:
        hosts(normal) == expected
        hosts(retained) == expected
        retained.applicationName == normal.applicationName

        cleanup:
        context?.close()

        where:
        properties                                                          | expected
        ['mongodb.uri': URI]                                                | ['uri.test:27111']
        ['mongodb.uri': URI, 'mongodb.host': 'example.test:27999']          | ['example.test:27999']
        ['mongodb.uri': URI, 'mongodb.hosts': ['a.test:1', 'b.test:2']]     | ['a.test:1', 'b.test:2']
    }

    @Unroll
    void "the retained named configuration connects where the normal one does for #properties"() {
        given:
        ApplicationContext context = run(properties)

        when:
        MongoClientSettings normal = context.getBean(NamedMongoConfiguration, Qualifiers.byName('one')).buildSettings()
        MongoClientSettings retained = DevelopmentMongoSettings.retainedSettings(context.getBean(RetainedNamedMongoConfiguration, Qualifiers.byName('one')))

        then:
        hosts(normal) == expected
        hosts(retained) == expected

        cleanup:
        context?.close()

        where:
        properties                                                                                          | expected
        ['mongodb.servers.one.uri': URI]                                                                    | ['uri.test:27111']
        ['mongodb.servers.one.uri': URI, 'mongodb.servers.one.host': 'example.test:27999']                  | ['uri.test:27111']
        ['mongodb.servers.one.uri': URI, 'mongodb.servers.one.hosts': ['a.test:1']]                         | ['uri.test:27111']
        ['mongodb.servers.one.uri': URI, 'mongodb.servers.one.cluster.hosts': ['example.test:27999']]       | ['example.test:27999']
    }

    void "a retained named client reports the application name of the normal one"() {
        given:
        ApplicationContext context = run(
            'micronaut.application.name': 'app',
            'mongodb.servers.one.uri': URI,
            'mongodb.servers.two.uri': URI)

        expect:
        ['one', 'two'].each { String server ->
            def qualifier = Qualifiers.byName(server)
            MongoClientSettings retained = DevelopmentMongoSettings.retainedSettings(context.getBean(RetainedNamedMongoConfiguration, qualifier))
            assert retained.applicationName == "app-$server".toString()
            assert retained.applicationName == context.getBean(NamedMongoConfiguration, qualifier).buildSettings().applicationName
        }

        cleanup:
        context?.close()
    }

    private static ApplicationContext run(Map<String, Object> properties) {
        ApplicationContext.run(['micronaut.dev.enabled': 'true'] + properties)
    }

    private static List<String> hosts(MongoClientSettings settings) {
        settings.clusterSettings.hosts*.toString()
    }
}
