package io.github.lynxus.spring.boot;

import io.github.lynxus.api.SqlExecutor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class GeneratedMapperBeanDefinitionRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final String MAPPER_METADATA_PATTERN =
        "classpath*:/META-INF/lynxus/mappers/*.properties";
    private static final String SCHEMA_VERSION = "1";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        List<LynxusProperties.MapperBinding> bindings = Binder.get(environment)
            .bind(
                "lynxus.mapper-bindings",
                Bindable.listOf(LynxusProperties.MapperBinding.class)
            )
            .orElse(List.of());
        validateBindings(registry, bindings);
        List<MapperMetadata> mapperMetadata = discoverMapperMetadata();
        for (LynxusProperties.MapperBinding binding : bindings) {
            registerGeneratedMappers(registry, binding, mapperMetadata);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    private void registerGeneratedMappers(
            BeanDefinitionRegistry registry,
            LynxusProperties.MapperBinding binding,
            List<MapperMetadata> mapperMetadata) {
        String executorBeanName = registerExecutor(registry, binding.getDataSource());
        for (MapperMetadata metadata : mapperMetadata) {
            if (matchesPackage(binding.getPackageName(), metadata.mapperPackage())) {
                registerGeneratedMapper(registry, metadata, executorBeanName);
            }
        }
    }

    private void registerGeneratedMapper(
            BeanDefinitionRegistry registry,
            MapperMetadata metadata,
            String executorBeanName) {
        String className = metadata.implementationClass();
        try {
            Class<?> implementationClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            Class<?> mapperInterface = findMapperInterface(implementationClass);
            if (mapperInterface == null) {
                throw new BeanDefinitionStoreException(
                    "Invalid generated Lynxus mapper " + className + ": no @Mapper interface is implemented");
            }
            if (!mapperInterface.getName().equals(metadata.mapperInterface())) {
                throw new BeanDefinitionStoreException(
                    "Invalid generated Lynxus mapper metadata for " + className
                        + ": mapper-interface does not match the generated class");
            }
            if (!hasSqlExecutorConstructor(implementationClass)) {
                throw new BeanDefinitionStoreException(
                    "Invalid generated Lynxus mapper " + className + ": missing public SqlExecutor constructor");
            }

            String beanName = Introspector.decapitalize(mapperInterface.getSimpleName());
            if (registry.containsBeanDefinition(beanName)) {
                BeanDefinition existing = registry.getBeanDefinition(beanName);
                throw new BeanDefinitionStoreException(
                    "Duplicate Lynxus mapper bean '" + beanName + "': " + className
                        + " conflicts with " + existing.getResourceDescription());
            }

            AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(implementationClass)
                .addConstructorArgValue(new RuntimeBeanReference(executorBeanName))
                .getBeanDefinition();
            registry.registerBeanDefinition(beanName, beanDefinition);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load generated Lynxus mapper " + className, e);
        }
    }

    private List<MapperMetadata> discoverMapperMetadata() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(MAPPER_METADATA_PATTERN);
            List<MapperMetadata> metadata = new ArrayList<>(resources.length);
            for (Resource resource : resources) {
                metadata.add(readMapperMetadata(resource));
            }
            return List.copyOf(metadata);
        } catch (IOException exception) {
            throw new BeanDefinitionStoreException(
                "Failed to discover Lynxus Mapper metadata", exception);
        }
    }

    private MapperMetadata readMapperMetadata(Resource resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        }
        String schemaVersion = requireMetadata(properties, "schema-version", resource);
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new BeanDefinitionStoreException(
                "Unsupported Lynxus Mapper metadata schema '" + schemaVersion
                    + "' in " + resource.getDescription());
        }
        return new MapperMetadata(
            requireMetadata(properties, "implementation-class", resource),
            requireMetadata(properties, "mapper-interface", resource),
            requireMetadata(properties, "mapper-package", resource));
    }

    private String requireMetadata(Properties properties, String key, Resource resource) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new BeanDefinitionStoreException(
                "Missing Lynxus Mapper metadata '" + key + "' in " + resource.getDescription());
        }
        return value.trim();
    }

    private void validateBindings(
            BeanDefinitionRegistry registry,
            List<LynxusProperties.MapperBinding> bindings) {
        for (int index = 0; index < bindings.size(); index++) {
            LynxusProperties.MapperBinding binding = bindings.get(index);
            requireText(binding.getPackageName(), "mapper-bindings[" + index + "].package-name");
            requireText(binding.getDataSource(), "mapper-bindings[" + index + "].data-source");
            if (!registry.containsBeanDefinition(binding.getDataSource())) {
                throw new BeanDefinitionStoreException(
                    "No DataSource bean named '" + binding.getDataSource()
                        + "' for Mapper package '" + binding.getPackageName() + "'");
            }
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                String otherPackage = bindings.get(otherIndex).getPackageName();
                if (binding.getPackageName().equals(otherPackage)
                        || packagesOverlap(binding.getPackageName(), otherPackage)) {
                    throw new BeanDefinitionStoreException(
                        "Mapper package bindings overlap: '" + otherPackage
                            + "' and '" + binding.getPackageName() + "'");
                }
            }
        }
    }

    private String registerExecutor(BeanDefinitionRegistry registry, String dataSourceName) {
        String executorBeanName = "lynxusSqlExecutor#" + dataSourceName;
        if (!registry.containsBeanDefinition(executorBeanName)) {
            AbstractBeanDefinition executorDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(SpringJdbcSqlExecutorFactoryBean.class)
                .addConstructorArgValue(new RuntimeBeanReference(dataSourceName))
                .getBeanDefinition();
            registry.registerBeanDefinition(executorBeanName, executorDefinition);
        }
        return executorBeanName;
    }

    private boolean packagesOverlap(String left, String right) {
        return left.startsWith(right + ".") || right.startsWith(left + ".");
    }

    private boolean matchesPackage(String bindingPackage, String mapperPackage) {
        return mapperPackage.equals(bindingPackage)
            || mapperPackage.startsWith(bindingPackage + ".");
    }

    private void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new BeanDefinitionStoreException(
                "Lynxus property '" + property + "' must not be blank");
        }
    }

    private Class<?> findMapperInterface(Class<?> implementationClass) {
        if (!Modifier.isPublic(implementationClass.getModifiers())) {
            return null;
        }
        for (Class<?> implementedInterface : implementationClass.getInterfaces()) {
            if (implementedInterface.isAnnotationPresent(io.github.lynxus.annotation.Mapper.class)) {
                return implementedInterface;
            }
        }
        return null;
    }

    private boolean hasSqlExecutorConstructor(Class<?> implementationClass) {
        for (Constructor<?> constructor : implementationClass.getConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0] == SqlExecutor.class) {
                return true;
            }
        }
        return false;
    }

    private record MapperMetadata(
            String implementationClass,
            String mapperInterface,
            String mapperPackage) {
    }
}
