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
package com.blackbuild.klum.ast;

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Namespace for annotations that describe explicit Builder-specific schema behavior.
 *
 * <p>This type is not an annotation and is unrelated to the generated {@code Foo_DSL.Builder} interface. It only groups
 * schema vocabulary whose meaning is specific to construction-time Builders.</p>
 */
public final class Builder {

    private Builder() {
        throw new AssertionError("Builder is an annotation namespace and cannot be instantiated");
    }

    /**
     * Projects a side-effect-free DSL Object query onto that object's generated public Builder contract.
     *
     * <p>The original method remains available on completed Models. Its generated Builder counterpart executes against
     * the current Builder state and may only read state that is present before and after materialization. The result must
     * not contain a DSL Object or Builder type.</p>
     *
     * <p>Calls into foreign non-DSL code are treated as a Schema Developer assertion that the call is observational;
     * KlumAST enforces only locally visible purity restrictions.</p>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @KlumCastValidator("com.blackbuild.klum.ast.compiler.internal.ast.BuilderQueryCheck")
    @Documented
    public @interface Query {
    }

    /**
     * Marks a method as Builder-only construction behavior.
     *
     * <p>The method is moved to the generated Builder, can change Builder state, and is absent from the completed DSL
     * Object. This is the canonical replacement for {@link Mutator}; the legacy spelling is promoted to this annotation
     * during compilation and both must not be combined on one method.</p>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @WriteAccess(WriteAccess.Type.MANUAL)
    @Documented
    public @interface Method {
    }

    /**
     * Projects one DSL Object parameter to its exact generated Builder type in the method's Builder-side contract.
     *
     * <p>The completed-Model method retains the declared Model type. Only the explicitly annotated position changes in
     * the linked Builder method; unannotated parameters keep their completed-state meaning.</p>
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @GroovyASTTransformationClass("com.blackbuild.klum.ast.compiler.internal.ast.BuilderInputTransformation")
    @Documented
    public @interface Input {
    }

    /**
     * Projects a DSL Object result to its exact generated Builder type in the method's Builder-side contract.
     *
     * <p>A successful Builder-side invocation returns an unsealed Builder in the active Construction session. The
     * annotation never converts a completed Model into composition; normal session, sealing, attachment, and ownership
     * checks remain authoritative.</p>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @KlumCastValidator("com.blackbuild.klum.ast.compiler.internal.ast.BuilderResultCheck")
    @Documented
    public @interface Result {
    }
}
