package io.github.lynxus.test.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicApiSurfaceTest {

    private static final Set<String> SUPPORTED_PUBLIC_TYPES = Set.of(
        "io.github.lynxus.JdbcAssembly",
        "io.github.lynxus.Lynxus",
        "io.github.lynxus.annotation.Batch",
        "io.github.lynxus.annotation.Delete",
        "io.github.lynxus.annotation.GeneratedKey",
        "io.github.lynxus.annotation.Insert",
        "io.github.lynxus.annotation.Mapper",
        "io.github.lynxus.annotation.Param",
        "io.github.lynxus.annotation.Result",
        "io.github.lynxus.annotation.Results",
        "io.github.lynxus.annotation.Select",
        "io.github.lynxus.annotation.Update",
        "io.github.lynxus.annotation.UseParameterBinder",
        "io.github.lynxus.annotation.UseRowMapper",
        "io.github.lynxus.annotation.UseSqlProvider",
        "io.github.lynxus.api.BatchExecutionPlan",
        "io.github.lynxus.api.BatchDefinition",
        "io.github.lynxus.api.BoundParameter",
        "io.github.lynxus.api.BoundSql",
        "io.github.lynxus.api.BoundSqlBuilder",
        "io.github.lynxus.api.BatchResult",
        "io.github.lynxus.api.ConfigurationException",
        "io.github.lynxus.api.CommandDefinition",
        "io.github.lynxus.api.ConnectionHandle",
        "io.github.lynxus.api.ConnectionHandleFactory",
        "io.github.lynxus.api.CursorCallback",
        "io.github.lynxus.api.ExecutionInterceptor",
        "io.github.lynxus.api.ExecutionOutcome",
        "io.github.lynxus.api.ExecutionPhase",
        "io.github.lynxus.api.ExecutionPlan",
        "io.github.lynxus.api.ExecutionPlugin",
        "io.github.lynxus.api.GeneratedKeyResult",
        "io.github.lynxus.api.JdbcExecutionState",
        "io.github.lynxus.api.LynxusException",
        "io.github.lynxus.api.MappingException",
        "io.github.lynxus.api.NonUniqueResultException",
        "io.github.lynxus.api.ParameterBinder",
        "io.github.lynxus.api.QueryDefinition",
        "io.github.lynxus.api.QueryExecutionPlan",
        "io.github.lynxus.api.QueryResult",
        "io.github.lynxus.api.ResultAssembler",
        "io.github.lynxus.api.ResultColumn",
        "io.github.lynxus.api.ResultRow",
        "io.github.lynxus.api.RowCursor",
        "io.github.lynxus.api.RowMapper",
        "io.github.lynxus.api.SqlExecutionException",
        "io.github.lynxus.api.SqlExecutor",
        "io.github.lynxus.api.SqlProvider",
        "io.github.lynxus.api.SqlResult",
        "io.github.lynxus.api.StatementOptions",
        "io.github.lynxus.api.TransactionCallback",
        "io.github.lynxus.api.TransactionDomain",
        "io.github.lynxus.api.TransactionDomainGuard",
        "io.github.lynxus.api.TransactionException",
        "io.github.lynxus.api.TransactionalExecutor",
        "io.github.lynxus.api.UpdateResult",
        "io.github.lynxus.interceptor.AuditExecutionInterceptor",
        "io.github.lynxus.interceptor.LoggingExecutionInterceptor",
        "io.github.lynxus.interceptor.SlowQueryExecutionInterceptor",
        "io.github.lynxus.jdbc.JdbcSqlExecutor",
        "io.github.lynxus.jdbc.TypeHandlerManager",
        "io.github.lynxus.runtime.ResultValueConverters",
        "io.github.lynxus.transaction.SimpleConnectionHandleFactory",
        "io.github.lynxus.transaction.SimpleTransactionDomainGuard",
        "io.github.lynxus.transaction.SimpleTransactionalExecutor"
    );
    @Test
    void exposesOnlySupportedTopLevelTypes() throws Exception {
        assertEquals(new TreeSet<>(SUPPORTED_PUBLIC_TYPES), discoverPublicTopLevelTypes());
    }

    @Test
    void keepsTypeHandlerManagerResultRoutingInternal() throws Exception {
        Class<?> resultHandler = Class.forName("io.github.lynxus.jdbc.TypeHandlerManager$ResultHandler");

        assertFalse(Modifier.isPublic(resultHandler.getModifiers()));
    }

    @Test
    void keepsApiClassesIndependentFromJdbcImplementations() throws Exception {
        Path apiClasses = Path.of("target", "classes", "io", "github", "lynxus", "api");
        try (var classFiles = Files.walk(apiClasses)) {
            for (Path classFile : classFiles.filter(path -> path.toString().endsWith(".class")).toList()) {
                String constantPool = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("io/github/lynxus/jdbc"),
                    () -> classFile + " references io.github.lynxus.jdbc");
            }
        }
    }

    @Test
    void keepsCompilerImplementationOutOfTheRuntimeArtifact() {
        Path compilerPackage = Path.of("target", "classes", "io", "github", "lynxus", "compile");
        Path freemarkerClasses = Path.of("target", "classes", "freemarker");

        assertFalse(Files.exists(compilerPackage));
        assertFalse(Files.exists(freemarkerClasses));
    }

    private Set<String> discoverPublicTopLevelTypes() throws IOException {
        Path classesDirectory = Path.of("target", "classes");
        try (var classFiles = Files.walk(classesDirectory.resolve("io/github/lynxus"))) {
            return classFiles
                .filter(path -> path.toString().endsWith(".class"))
                .map(classesDirectory::relativize)
                .map(Path::toString)
                .map(path -> path.substring(0, path.length() - ".class".length()))
                .map(path -> path.replace('/', '.').replace('\\', '.'))
                .filter(className -> !className.contains("$"))
                .filter(this::isPublic)
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private boolean isPublic(String className) {
        try {
            return Modifier.isPublic(Class.forName(className, false, getClass().getClassLoader()).getModifiers());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot inspect compiled type " + className, exception);
        }
    }

}
