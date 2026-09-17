# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples under `docs-examples/csfle-python`
that are present but disabled, or that deviate from the Java example because the direct port does
not compile or does not behave like the Java example yet. It is the bug-fixing task list for
the Python compiler (`micronaut-inject-python` / `micronaut-context-python`); every row references a
`TODO(python)` comment in the sources or a workaround described below.

The Python examples are compiled by every build and their tests run with
`./gradlew pythonCheck -Ppython-ci` (the "Python CI" GitHub workflow), which needs a container
runtime for the MongoDB test container of `NamedClientServiceTest`.

## Reconciliation

- Last generated active `@Disabled` count: 0.
- Last generated command: `rg -n "@Disabled\(" docs-examples/csfle-python/src/test/python`.
- Last full-suite command: `./gradlew :micronaut-docs-examples:micronaut-csfle-python:test -Ppython-ci`.
- Last full-suite result: build successful, 3 tests executed, 0 skipped, 0 failures.

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets.
  The Micronaut and driver classes are imported from their Java packages (`micronaut.configuration.mongo.core`,
  `com.mongodb`, `com.mongodb.reactivestreams.client`, `org.bson`).
- `MongoClientSettingsBuilderCustomizer` is implemented by a plain `@Singleton` class whose `customize` method keeps
  the Java name; named injection is `Annotated[MongoClient, Inject, Named("another")]`; methods are snake_case
  (`database_names`).
- The tests are `@MicronautTest(startApplication=False)` classes. The customizer tests check the settings built by
  `DefaultMongoConfiguration`/`NamedMongoConfiguration.buildSettings()` (creating a client with auto-encryption
  enabled would need the `mongocryptd` daemon); the customizer is guarded by a hidden
  `@Requires(property="spec.name", value="AutoEncryptionCustomizerTest")` in every language so the named client test
  creates a plain client. The container URI of the `another` server is supplied to the `mongodb` environment by the
  Java `io.micronaut.mongodb.support.MongoTestPropertySourceLoader`.

## Active `@Disabled` Tests

None.

## Commented Unsupported Snippet Ports

None.

## Workarounds Kept In Snippets

| Target | Reason |
| --- | --- |
| `io.micronaut.mongodb.support.MongoTestPropertySourceLoader` (Java, `src/test/java`) | `TestPropertyProvider.getProperties()` is called by Micronaut Test before the application context, and with it the GraalPy runtime, exists, so a Python test class cannot provide the container URI. An `ApplicationContextConfigurer.configure(ApplicationContext)` adding the property is too late as well: the `mongo-reactive` package is enabled by `@Requires(property = "mongodb")`, and the bean configurations are read before that callback runs, so the property is supplied by an environment-gated `PropertySourceLoader` instead. |

## Intentionally Unsupported Snippet Targets

None.

## java.type usages

None.
