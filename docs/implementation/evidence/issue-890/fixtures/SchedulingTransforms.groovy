/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Required
import com.blackbuild.klum.ast.Optional
import com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper
import com.blackbuild.klum.ast.compiler.internal.ast.converters.RequiredToValidateTransformation
import com.blackbuild.klum.ast.compiler.internal.ast.converters.OptionalToValidateTransformation
import com.blackbuild.klum.cast.validation.KlumCastTransformation
import com.blackbuild.klum.cast.validation.ValidationHandler
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.*
import org.codehaus.groovy.transform.*
import java.lang.annotation.ElementType
import java.lang.annotation.Target

// These classes exist only in a temporary test classpath, never the shipped compiler.
class PreprocessingTrace {
    static List<String> events = []
}

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class CastSchedulingProbe implements ASTTransformation {
    void visit(ASTNode[] nodes, SourceUnit source) {
        PreprocessingTrace.events << 'cast'
        new KlumCastTransformation().visit(nodes, source)
    }
}

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class SchemaSchedulingProbe extends AbstractASTTransformation {
    void visit(ASTNode[] nodes, SourceUnit source) {
        sourceUnit = source
        PreprocessingTrace.events << 'preprocess'
        source.AST.classes.toList().each { schema ->
            if (schema.annotationDefinition || schema.interface || !DslAstHelper.isDSLObject(schema)) return
            PreprocessingTrace.events << "eligible:$schema.name".toString()
            def declarations = [schema] + schema.fields.findAll { it.owner == schema } +
                    schema.methods.findAll { it.declaringClass == schema }
            declarations.each { declaration ->
                List<AnnotationNode> copies = []
                declaration.annotations.toList().each { use ->
                    def markers = use.classNode.annotations.findAll { it.classNode.name == 'ProbePropagate' }
                    use.classNode.annotations.findAll { it.classNode.name == 'ProbePropagate$List' }.each { container ->
                        markers.addAll(container.getMember('value').expressions.collect { it.value })
                    }
                    markers.each { marker ->
                        ClassExpression selected = marker.getMember('value')
                        assert selected.type.resolved
                        if (selected.type.name == DSL.name) {
                            addError('Propagation of @DSL is unsupported', use)
                            return
                        }
                        def requiredMembers = selected.type.typeClass.declaredMethods.findAll { it.defaultValue == null }
                        if (!requiredMembers.empty) {
                            addError("@${use.classNode.name} propagates @${selected.type.name}: missing defaults for ${requiredMembers*.name}", use)
                            return
                        }
                        def allowed = selected.type.typeClass.getAnnotation(Target)?.value()
                        ElementType actual = declaration instanceof ClassNode ? ElementType.TYPE :
                                declaration instanceof FieldNode ? ElementType.FIELD : ElementType.METHOD
                        if (allowed != null && !(actual in allowed)) {
                            addError("@${use.classNode.name} propagates @${selected.type.name}: illegal target $actual", use)
                            return
                        }
                        def copied = new AnnotationNode(selected.type)
                        copied.setSourcePosition(use)
                        copied.putNodeMetaData('probe.origin', use.classNode.name)
                        declaration.addAnnotation(copied)
                        PreprocessingTrace.events << "copy:${selected.type.name}:${declaration.name}".toString()
                        copies << copied
                    }
                }
                if (@VALIDATE@ && !copies.empty) {
                    if (@REVISIT@) {
                        // Experimental invalidation of engine metadata: not a supported integration API.
                        declaration.annotations.each { it.removeNodeMetaData(ValidationHandler.METADATA_KEY) }
                        declaration.annotations.each { effective -> validateCopy(effective, declaration, copies) }
                    } else copies.each { copied -> validateCopy(copied, declaration, copies) }
                }
            }
            if (@COORDINATE@) {
                // Intentionally retain real local registrations to expose double execution.
                def fields = declarations.findAll { it instanceof FieldNode }
                fields.each { field ->
                    field.annotations.findAll { it.classNode.name == Required.name }.each { use ->
                        PreprocessingTrace.events << 'required'
                        new RequiredToValidateTransformation().visit([use, field] as ASTNode[], source)
                    }
                }
                fields.each { field ->
                    field.annotations.findAll { it.classNode.name == Optional.name }.each { use ->
                        PreprocessingTrace.events << 'optional'
                        new OptionalToValidateTransformation().visit([use, field] as ASTNode[], source)
                    }
                }
                PreprocessingTrace.events << 'lowered'
            }
        }
    }

    private void validateCopy(AnnotationNode copied, AnnotatedNode declaration, List<AnnotationNode> contributions) {
        // Deliberately exercises the existing engine, not a reimplementation of its checks.
        ValidationHandler.validateAnnotation(copied, declaration).each { diagnostic ->
            addError("@${contributions.first().getNodeMetaData('probe.origin')} propagates @${contributions.first().classNode.name}: " +
                    "invalid use on ${declaration.name}: [${diagnostic.code}] ${diagnostic.message}", copied)
        }
    }
}

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class ForeignSchedulingProbe implements ASTTransformation {
    void visit(ASTNode[] nodes, SourceUnit source) {
        PreprocessingTrace.events << "foreign:${nodes[1].name}".toString()
        ClassNode owner = nodes[1] instanceof ClassNode ? nodes[1] : nodes[1].declaringClass
        String method = "foreign_${nodes[1].name.tokenize('.').last()}"
        assert owner.getDeclaredMethods(method).empty : 'foreign transform executed twice'
        owner.addMethod(method, 1, ClassHelper.STRING_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY,
                new ReturnStatement(new ConstantExpression('generated')))
    }
}

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class LoweringAuditProbe implements ASTTransformation {
    void visit(ASTNode[] nodes, SourceUnit source) {
        source.AST.classes.each { schema ->
            schema.fields.each { field ->
                int count = field.annotations.count { it.classNode.name == 'com.blackbuild.klum.ast.Validate' }
                if (count) PreprocessingTrace.events << "validate-count:${field.name}:$count".toString()
            }
        }
    }
}
