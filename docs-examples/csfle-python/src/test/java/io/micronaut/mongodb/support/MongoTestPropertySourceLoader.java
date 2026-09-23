package io.micronaut.mongodb.support;

import io.micronaut.context.env.ActiveEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.mongodb.testcontainers.MongoDb;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Supplies the URI of the MongoDB test container as the {@code another} named server to the tests run with the
 * {@value #MONGODB_ENVIRONMENT} environment, like the {@code TestPropertyProvider} of the Java, Kotlin and Groovy
 * example tests does.
 * <p>
 * The loader is written in Java because Micronaut Test calls {@code TestPropertyProvider} before the application
 * context, and with it the GraalPy runtime, exists, so a Python test class cannot supply the container properties.
 * It is a {@link PropertySourceLoader} rather than an {@code ApplicationContextConfigurer} because the property
 * must be visible when the bean configurations are read: the {@code mongo-reactive} package is enabled by
 * {@code @Requires(property = "mongodb")}, which is evaluated before {@code ApplicationContextConfigurer.configure(ApplicationContext)}
 * runs. The loader is registered in {@code META-INF/services/io.micronaut.context.env.PropertySourceLoader}.
 */
public class MongoTestPropertySourceLoader implements PropertySourceLoader {

    public static final String MONGODB_ENVIRONMENT = "mongodb";

    @Override
    public Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
        return Optional.empty();
    }

    @Override
    public Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
        if (Environment.DEFAULT_NAME.equals(resourceName) && MONGODB_ENVIRONMENT.equals(activeEnvironment.getName())) {
            return Optional.of(PropertySource.of(MONGODB_ENVIRONMENT + "-test", Map.of(
                "mongodb.servers.another.uri", MongoDb.getProperties().get("mongodb.uri")
            )));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> read(String name, InputStream input) {
        return Map.of();
    }
}
