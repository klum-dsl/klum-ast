/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.groovy.configdsl.transform.ast.modules;

import com.blackbuild.groovy.configdsl.transform.DSL;
import groovy.transform.CompilationUnitAware;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Initial configurator for Klum. Parses the classpath for other transformations and registers those with
 * the Compiler as well.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class ModuleConfigurator extends AbstractASTTransformation implements CompilationUnitAware {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    private CompilationUnit compilationUnit;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        if (!((AnnotationNode) nodes[0]).getClassNode().equals(DSL_CONFIG_ANNOTATION))
            throw new IllegalStateException("ModuleConfigurator should only be called for a class annotated with @DSL");

        for (String transformationClass : findTransformations()) {
            ASTTransformationCustomizer customizer = new ASTTransformationCustomizer(DSL.class, transformationClass);
            customizer.setCompilationUnit(compilationUnit);
            compilationUnit.addPhaseOperation(customizer, customizer.getPhase().getPhaseNumber());
        }
    }

    private List<String> findTransformations() {

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = loader.getResources("META-INF/klum-ast/KlumAstTransformation");

            List<String> names = new ArrayList<>();

            while (resources.hasMoreElements())
                addTransformationsFrom(resources.nextElement(), names);

            return names;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addTransformationsFrom(URL descriptor, List<String> names) {
        try (InputStream in = descriptor.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"))
        ) {
            String line;
            while ((line = reader.readLine()) != null)
                parseLine(line, names);

        } catch (IOException e) {
            throw new IllegalStateException("Error reading configuration file", e);
        }
    }

    private void parseLine(String line, List<String> names) {
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) line = line.substring(0, commentIndex);
        line = line.trim();
        int n = line.length();
        if (n != 0) {
            names.add(line);
        }
    }

    @Override
    public void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit;
    }
}
