package io.github.lynxus.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.lynxus.compile.LynxusProcessor;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationPackageCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapperCompilesUsingOnlyLynxusAnnotations() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve("io/github/lynxus/test/annotationfixture/LiteMapper.java");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package io.github.lynxus.test.annotationfixture;

            import io.github.lynxus.annotation.Mapper;
            import io.github.lynxus.annotation.Param;
            import io.github.lynxus.annotation.Select;

            @Mapper
            public interface LiteMapper {
                @Select("SELECT #{value}")
                Long find(@Param("value") Long value);
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean succeeded;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(mapperSource)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LynxusProcessor()));
            succeeded = task.call();
        }

        assertTrue(succeeded, () -> diagnostics.getDiagnostics().toString());
        assertTrue(Files.exists(generatedDirectory.resolve(
            "io/github/lynxus/test/annotationfixture/LiteMapperImpl.java")));
    }

    @Test
    void generatesOneSpringMetadataResourcePerMapper() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("metadata-sources");
        Path classesDirectory = temporaryDirectory.resolve("metadata-classes");
        Path generatedDirectory = temporaryDirectory.resolve("metadata-generated");
        Path mapperSource = sourceDirectory.resolve("io/github/lynxus/test/metadatafixture/MetadataMapper.java");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package io.github.lynxus.test.metadatafixture;

            import io.github.lynxus.annotation.Mapper;
            import io.github.lynxus.annotation.Param;
            import io.github.lynxus.annotation.Select;

            @Mapper
            public interface MetadataMapper {
                @Select("SELECT #{value}")
                Long find(@Param("value") Long value);
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean succeeded;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(mapperSource)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LynxusProcessor()));
            succeeded = task.call();
        }

        assertTrue(succeeded, () -> diagnostics.getDiagnostics().toString());
        Path metadata = classesDirectory.resolve(
            "META-INF/lynxus/mappers/io.github.lynxus.test.metadatafixture.MetadataMapperImpl.properties");
        assertTrue(Files.isRegularFile(metadata));
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertTrue(properties.stringPropertyNames().containsAll(List.of(
            "schema-version", "implementation-class", "mapper-interface", "mapper-package")));
        assertTrue(properties.stringPropertyNames().stream()
            .allMatch(name -> !properties.getProperty(name).isBlank()));
        assertTrue(properties.getProperty("schema-version").equals("1"));
        assertTrue(properties.getProperty("implementation-class")
            .equals("io.github.lynxus.test.metadatafixture.MetadataMapperImpl"));
        assertTrue(properties.getProperty("mapper-interface")
            .equals("io.github.lynxus.test.metadatafixture.MetadataMapper"));
        assertTrue(properties.getProperty("mapper-package")
            .equals("io.github.lynxus.test.metadatafixture"));
        try (var files = Files.list(classesDirectory.resolve("META-INF/lynxus/mappers"))) {
            assertTrue(files.count() == 1);
        }
    }

    @Test
    void sourceTreeDoesNotContainLegacyIbatisAnnotationPackage() throws Exception {
        Path sourceRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.exists(sourceRoot)) {
            sourceRoot = Path.of(System.getProperty("user.dir"), "lynxus-core", "src", "main", "java");
        }

        assertFalse(Files.exists(sourceRoot.resolve("org/apache/ibatis/annotations")));
    }
}
