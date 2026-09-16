package io.github.lynxus.compile;

import io.github.lynxus.annotation.Mapper;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that generates ordinary Java Mapper implementations.
 * 
 * @author lynxus
 * @since 2024/09/29
 */
@SupportedAnnotationTypes("io.github.lynxus.annotation.Mapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class LynxusProcessor extends AbstractProcessor {
    
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    
    private CompilePipeline compilePipeline;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        
        this.compilePipeline = new CompilePipeline(elementUtils, typeUtils, messager, filer);
        
        messager.printMessage(Diagnostic.Kind.NOTE, "Lynxus Processor initialized");
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        
        messager.printMessage(Diagnostic.Kind.NOTE, "Lynxus Processing started...");
        
        try {
            processMapperAnnotations(roundEnv);
            messager.printMessage(Diagnostic.Kind.NOTE, "Lynxus Processing completed");
        } catch (RuntimeException exception) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Unexpected Lynxus processor failure: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
        }
        
        return true;
    }
    
    
    private void processMapperAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> mapperElements = roundEnv.getElementsAnnotatedWith(Mapper.class);
        List<TypeElement> mapperTypes = mapperElements.stream()
            .filter(TypeElement.class::isInstance)
            .map(TypeElement.class::cast)
            .sorted(Comparator.comparing(type -> type.getQualifiedName().toString()))
            .toList();

        for (TypeElement typeElement : mapperTypes) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Processing Mapper: " + typeElement.getQualifiedName());
            try {
                generateZeroReflectionMapperImpl(typeElement);
            } catch (IOException exception) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write mapper implementation for " + typeElement.getQualifiedName()
                        + ": " + exception.getMessage(), typeElement);
            } catch (RuntimeException exception) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Unexpected compiler failure for Mapper " + typeElement.getQualifiedName()
                        + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    typeElement);
            }
        }
    }
    
    /**
     * Generates one Mapper implementation.
     */
    private void generateZeroReflectionMapperImpl(TypeElement mapperInterface) throws IOException {
        try {
            if (!compilePipeline.supports(mapperInterface)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                    "Skipping unsupported interface: " + mapperInterface.getQualifiedName());
                return;
            }
            
            String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
            String className = mapperInterface.getSimpleName() + "Impl";
            String qualifiedClassName = packageName + "." + className;
            
            String javaCode = compilePipeline.compileMapper(mapperInterface);

            JavaFileObject builderFile = filer.createSourceFile(qualifiedClassName);
            try (Writer writer = builderFile.openWriter()) {
                writer.write(javaCode);
            }
            writeMapperMetadata(
                qualifiedClassName,
                mapperInterface.getQualifiedName().toString(),
                packageName);
            
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "Generated zero-reflection mapper: " + qualifiedClassName);
                
        } catch (CompilePipeline.CompileException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to compile mapper implementation: " + e.getMessage(),
                e.element() != null ? e.element() : mapperInterface);
        }
    }

    private void writeMapperMetadata(
            String implementationClass,
            String mapperInterface,
            String mapperPackage) throws IOException {
        String resourceName = "META-INF/lynxus/mappers/" + implementationClass + ".properties";
        var resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
        try (Writer writer = resource.openWriter()) {
            writer.write("schema-version=1\n");
            writer.write("implementation-class=" + implementationClass + "\n");
            writer.write("mapper-interface=" + mapperInterface + "\n");
            writer.write("mapper-package=" + mapperPackage + "\n");
        }
    }
}
