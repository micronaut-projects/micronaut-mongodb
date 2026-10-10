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
package io.micronaut.configuration.mongo.core.dev;

import com.mongodb.MongoClientSettings;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.CodecRegistryBuilder;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.Qualifier;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanType;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps the MongoDB clients in step with the code and the configuration in development mode. It exists only in
 * development mode, so nothing of it is on the path of an operation.
 *
 * <ul>
 *     <li>A class change applied in place that touches a class a codec may have been built from, a class that is not
 *     a bean, such as a document class, or a codec, or that retires a classloader, recreates the client beans of the
 *     generation, the {@link GenerationMongoClient}s, and the default {@link MongoClientSettings}: they build their
 *     codec registry again, and the beans that received them are recreated on top of them. The
 *     {@link RetainedMongoClient}s, which hold no codec of the application, stay as they are.</li>
 *     <li>A codec, codec registry, codec registry builder, command or connection pool listener or settings
 *     customizer definition registered or removed recreates the MongoDB configurations, which received them, and so
 *     everything built from them, the retained clients among them.</li>
 *     <li>A change under {@code mongodb} applied in place recreates the retained clients from the rebound
 *     configuration. A change that restarts the application releases them, as {@code @Retain} declares.</li>
 * </ul>
 *
 * <p>A change that restarts the application is ignored: the next context builds its codecs from its own classes. Each
 * bean is recreated through {@link WatchableBeanContext#recreate(Object)}; a context that does not track bean
 * dependencies recreates nothing. The watches run after those of other modules, so that a serializer registry another
 * module recreates for the same change is in place before the codecs are built again. The reloader holds the context
 * only, never a MongoDB bean.</p>
 *
 * @author graemerocher
 * @since 6.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentMongoReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentMongoReloader.class);

    /**
     * The types of a class whose change makes the codec registries stale whether or not it is a bean.
     */
    private static final List<Class<?>> CODEC_TYPES = List.of(Codec.class, CodecProvider.class, CodecRegistry.class, CodecRegistryBuilder.class);

    /**
     * The types of the beans the configurations receive.
     */
    private static final List<Class<?>> CONTRIBUTION_TYPES = List.of(
        Codec.class,
        CodecRegistry.class,
        CodecRegistryBuilder.class,
        CommandListener.class,
        ConnectionPoolListener.class,
        MongoClientSettingsBuilderCustomizer.class
    );

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentMongoReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.configuration(MongoSettings.PREFIX).watchReloading(new ConfigurationChangeWatcher());
            watchable.definitions().qualifier(new ContributionQualifier()).watch(new ContributionDefinitionsWatcher());
            watchable.classChanges().watch(new ClassWatcher());
        }
    }

    private void onClassChange(ClassChangeEvent change) {
        if (change.strategy() == ReloadStrategy.RESTART) {
            // the next context builds its codecs from its own classes
            return;
        }
        if (!change.retiredLoaders().isEmpty()) {
            // the default codec registry builder of a configuration caches the discriminator classes of its packages,
            // which may be of the retired loader: such a configuration is recreated, with what was built from it
            recreateDiscriminatorCaches();
            recreate(List.of(MongoClientSettings.class, GenerationMongoClient.class), "a reload retired a classloader");
            return;
        }
        for (ClassChange classChange : change.changes()) {
            if (classChange.kind() != ClassChange.Kind.ADDED && mayBeEncoded(classChange.className(), change.newLoader())) {
                recreate(List.of(MongoClientSettings.class, GenerationMongoClient.class), classChange.className() + " changed");
                return;
            }
        }
    }

    /**
     * Whether a codec of a registry may have been built from the class: a codec type, a class a client of the
     * generation asked a codec for, a bean class included, or a class that is not a bean, such as a document class a
     * POJO or a Micronaut Serialization codec may encode. A bean that changes is otherwise recreated by the context,
     * with the beans that received it.
     */
    private boolean mayBeEncoded(String className, ClassLoader loader) {
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            // removed, or not loadable on its own: a codec may have been built from what it was
            return true;
        }
        for (Class<?> codecType : CODEC_TYPES) {
            if (codecType.isAssignableFrom(type)) {
                return true;
            }
        }
        return isCodecRequested(className) || !isBean(className);
    }

    private boolean isCodecRequested(String className) {
        for (BeanRegistration<GenerationMongoClient> registration : beanContext.getActiveBeanRegistrations(GenerationMongoClient.class)) {
            if (registration.bean().isCodecRequested(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recreates the configurations whose default codec registry builder may cache discriminator classes: those with
     * packages and without Micronaut Serialization. The retained clients, built from them, are recreated too.
     */
    private void recreateDiscriminatorCaches() {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        List<Object> configurations = new ArrayList<>();
        for (BeanRegistration<AbstractMongoConfiguration> registration : beanContext.getActiveBeanRegistrations(AbstractMongoConfiguration.class)) {
            AbstractMongoConfiguration configuration = registration.bean();
            if (!configuration.isUseSerde() && !configuration.getPackageNames().isEmpty()) {
                add(configurations, configuration);
            }
        }
        if (!configurations.isEmpty()) {
            LOG.debug("Recreating the MongoDB configurations with packages: a reload retired a classloader");
        }
        for (Object configuration : configurations) {
            context.recreate(configuration);
        }
    }

    /**
     * Whether the context has a definition of the class, or of a proxy of it. The references are matched by name, so
     * that no definition is loaded.
     */
    private boolean isBean(String className) {
        int lastDot = className.lastIndexOf('.');
        String prefix = className.substring(0, lastDot + 1) + '$' + className.substring(lastDot + 1);
        String definition = prefix + "$Definition";
        for (BeanDefinitionReference<?> reference : beanContext.getBeanDefinitionReferences()) {
            String name = reference.getBeanDefinitionName();
            if (name.equals(definition) || name.startsWith(definition + '$') || name.startsWith(prefix + "$Intercepted$Definition")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recreates the singletons of the types the context has created, and the beans that received them.
     *
     * @param types The types
     * @param reason Why, for the log
     */
    private void recreate(List<Class<?>> types, String reason) {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        // taken first: recreating one destroys the beans that received it, as the graph records them
        List<Object> beans = new ArrayList<>();
        for (Class<?> type : types) {
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(type)) {
                add(beans, registration.bean());
            }
        }
        if (beans.isEmpty()) {
            return;
        }
        LOG.debug("Recreating the MongoDB beans {}: {}", types, reason);
        for (Object bean : beans) {
            // false for a bean destroyed with one recreated before it, and for all of them in a context that does
            // not track bean dependencies
            context.recreate(bean);
        }
    }

    private static void add(List<Object> beans, Object bean) {
        for (Object taken : beans) {
            if (taken == bean) {
                return;
            }
        }
        beans.add(bean);
    }

    private static boolean isContribution(BeanType<?> candidate) {
        Class<?> beanType = candidate.getBeanType();
        for (Class<?> type : CONTRIBUTION_TYPES) {
            if (type.isAssignableFrom(beanType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recreates the retained clients from the configuration rebound in place.
     */
    private final class ConfigurationChangeWatcher implements ReloadingConfigurationWatcher, Ordered {
        @Override
        public Outcome onChange(ConfigurationChange change) {
            if (change.initial()) {
                return Outcome.IGNORED;
            }
            recreate(List.of(RetainedMongoClient.class), "the configuration under " + MongoSettings.PREFIX + " changed");
            return Outcome.APPLIED;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    /**
     * Selects the beans the configurations receive: codecs, codec registries and their builder, listeners and settings
     * customizers.
     */
    private static final class ContributionQualifier implements Qualifier<Object> {
        @Override
        public <B extends BeanType<Object>> Stream<B> reduce(Class<Object> beanType, Stream<B> candidates) {
            return candidates.filter(DevelopmentMongoReloader::isContribution);
        }

        @Override
        public boolean doesQualify(Class<Object> beanType, BeanType<Object> candidate) {
            return isContribution(candidate);
        }

        @Override
        public String toString() {
            return "codecs, listeners and settings customizers";
        }
    }

    /**
     * Recreates the configurations when a bean they receive is registered or removed. The first batch is what they
     * received.
     */
    private final class ContributionDefinitionsWatcher implements BeanDefinitionWatcher<Object>, Ordered {
        @Override
        public void onChange(BeanDefinitionChange<Object> change) {
            if (!change.initial() && (!change.added().isEmpty() || !change.removed().isEmpty())) {
                recreate(List.of(AbstractMongoConfiguration.class), "codec, listener or customizer definitions changed");
            }
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    /**
     * Follows a class change applied in place.
     */
    private final class ClassWatcher implements ClassChangeWatcher, Ordered {
        @Override
        public void onChange(ClassChangeEvent change) {
            onClassChange(change);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
